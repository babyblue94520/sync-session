package pers.clare.session;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ConcurrentReferenceHashMap;
import pers.clare.session.constant.InvalidateBy;
import pers.clare.session.exception.SyncSessionException;
import pers.clare.session.listener.RequestSessionInvalidateListener;
import pers.clare.session.util.SessionUtil;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Function;

public class SyncSessionServiceImpl<T extends SyncSession> implements SyncSessionService<T>, InitializingBean, DisposableBean {
    private static final Logger log = LogManager.getLogger();

    public static final String SPLIT = ",";
    // session id 長度
    public static final int ID_LENGTH = 32;

    // 當 session id 重複時，嘗試重建次數
    public static final int MAX_RETRY_INSERT = 5;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    // 本地 session 緩存
    private final ConcurrentMap<String, T> sessions = new ConcurrentHashMap<>();

    // 阻止重复执行
    private final ConcurrentMap<String, Long> invalidates = new ConcurrentReferenceHashMap<>();

    private final List<RequestSessionInvalidateListener> invalidateListeners = new CopyOnWriteArrayList<>();

    // 反建構的 session class
    private final Class<T> sessionClass;

    // session 最大存活時間
    private long maxInactiveInterval;

    // 本地緩存 session 時間
    private long updateInterval;

    // session 實際管理
    private final SyncSessionStore<T> store;

    private String invalidateTopic;

    private String clearTopic;

    private Function<? super String, ? extends T> findSession;

    @Autowired
    private SyncSessionProperties properties;

    @Autowired
    private SyncSessionEventService sessionEventService;

    @SuppressWarnings("unchecked")
    public SyncSessionServiceImpl(
             DataSource dataSource
    ) {
        this.sessionClass = (Class<T>) SessionUtil.getSessionClass(this.getClass());
        this.store = new SyncSessionStoreImpl<>(dataSource, this.sessionClass);
    }

    @Override
    public void afterPropertiesSet() {
        this.maxInactiveInterval = properties.getTimeout().toMillis();
        this.updateInterval = maxInactiveInterval / 2;
        // 更新 session 排程間隔
        long delay = maxInactiveInterval / 10;
        this.findSession = (id) -> {
            long now = System.currentTimeMillis();
            T session = store.find(id, now - maxInactiveInterval);
            if (session != null) {
                session.maxInactiveInterval = maxInactiveInterval;
            }
            return session;
        };

        // 監聽 session 清除或註銷事件
        if (sessionEventService == null || properties.getTopic() == null) {
            this.invalidateTopic = this.clearTopic = null;
        } else {
            this.invalidateTopic = properties.getTopic() + ".invalidate";
            this.clearTopic = properties.getTopic() + ".clear";
            sessionEventService.onConnected(sessions::clear);
            sessionEventService.addListener(invalidateTopic, this::invalidateHandler);
            sessionEventService.addListener(clearTopic, this::clearHandler);
        }
        // 排程檢查 Session 狀態
        executor.scheduleWithFixedDelay(this::batchUpdate, delay, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        executor.shutdown();
        log.info("{} executor shutdown...", "Session");
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                throw new InterruptedException("Session executor did not shut down gracefully within 30 seconds. Proceeding with forceful shutdown");
            }
            log.info("Session executor shutdown complete");
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }

    public SyncSessionProperties getProperties() {
        return properties;
    }

    public T find(String id) {
        if (id == null || id.length() != ID_LENGTH) return null;
        T session = findFromLocal(id);
        if (session != null) return session;
        return sessions.computeIfAbsent(id, findSession);
    }

    public T create(
            long accessTime
            , String userAgent
            , String ip
    ) {
        try {
            T session = sessionClass.getDeclaredConstructor().newInstance();
            session.createTime = accessTime;
            session.maxInactiveInterval = maxInactiveInterval;
            session.lastAccessTime = accessTime;
            session.lastUpdateAccessTime = accessTime;
            session.userAgent = userAgent;
            session.ip = ip;
            return doInsert(session, 0);
        } catch (Exception e) {
            throw new SyncSessionException(e);
        }
    }

    public int update(T session) {
        if (session == null) return 0;
        int count = store.update(session);
        if (count > 0) {
            session.refresh.block();
            notifyClear(session.id);
        }
        return count;
    }

    public void invalidate(T session) {
        if (session == null) return;
        invalidate(session.id);
    }

    public void invalidate(String id) {
        if (id == null || id.length() != ID_LENGTH) return;
        doInvalidate(id);
    }

    private T doInsert(T session, int count) {
        try {
            session.id = generateUUIDString(UUID.randomUUID());
            store.insert(session);
            sessions.put(session.id, session);
            return session;
        } catch (SyncSessionException e) {
            SQLException sqlException = e.getSqlException();
            if (sqlException instanceof SQLIntegrityConstraintViolationException
                    && sqlException.getErrorCode() == 1062
                    && count < MAX_RETRY_INSERT
            ) {
                // retry where uuid is exist
                return doInsert(session, count + 1);
            }
            throw e;
        } catch (Exception e) {
            throw new SyncSessionException(e);
        }
    }

    private void invalidateHandler(String body) {
        log.debug("invalidate event body:{}", body);
        String[] array = body.split(SPLIT);
        if (array.length < 2) return;
        String id = array[0];
        String username = array[1];
        if (invalidates.remove(id) != null) return;
        sessions.remove(id);
        if (invalidateListeners.size() > 0) {
            for (RequestSessionInvalidateListener invalidateListener : invalidateListeners) {
                invalidateListener.onInvalidate(id, username, InvalidateBy.NOTICE);
            }
        }
    }

    private void clearHandler(String body) {
        log.debug("clear event body:{}", body);
        T session = sessions.get(body);
        if (session == null) return;
        // 減少多餘的移除行為
        if (session.refresh.isBlock()) return;
        log.debug("do clear {}", body);
        sessions.remove(body);
    }

    private void doInvalidate(String id) {
        T session = sessions.remove(id);
        String username;
        if (session == null) {
            username = store.findUsername(id);
        } else {
            session.valid = false;
            username = session.getUsername();
        }
        if (store.delete(id) == 0) return;
        triggerInvalidateEvent(id, username);
    }

    public RequestSessionInvalidateListener addInvalidateListeners(RequestSessionInvalidateListener listener) {
        if (listener == null) return null;
        invalidateListeners.add(listener);
        return listener;
    }

    private void batchUpdate() {
        log.debug("batchUpdate start...");
        try {
            long t = System.currentTimeMillis();
            long now = t;
            long check = now - maxInactiveInterval;
            int originSize = sessions.size();
            if (originSize > 0) {
                List<T> updates = new ArrayList<>(originSize);
                Iterator<T> iterator = sessions.values().iterator();
                T session;
                while (iterator.hasNext()) {
                    session = iterator.next();
                    // 更新活躍 Session 的 lastAccessTime 到資料庫
                    if (session.lastAccessTime == session.lastUpdateAccessTime) continue;
                    if (session.lastAccessTime + updateInterval > now) {
                        session.lastUpdateAccessTime = session.lastAccessTime;
                        updates.add(session);
                    }
                }

                // 更新
                t = System.currentTimeMillis();
                int count = store.updateLastAccessTime(updates);
                log.debug("update session:{} real:{} {}ms", updates.size(), count, System.currentTimeMillis() - t);
            }
            // 註銷
            List<SyncSessionId> ids = store.findAllInvalidate(check);
            if (ids.size() > 0) {
                String id;
                for (SyncSessionId syncSessionId : ids) {
                    try {
                        id = syncSessionId.getId();
                        sessions.remove(id);
                        if (store.delete(id) == 0) continue;
                        triggerInvalidateEvent(id, syncSessionId.getUsername());
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                }
            }
            log.debug("invalidate session:{} {}ms", ids.size(), System.currentTimeMillis() - t);
            log.debug("batchUpdate {}>{} {}ms", originSize, sessions.size(), (System.currentTimeMillis() - now));
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    private void triggerInvalidateEvent(String id, String username) {
        if (invalidateListeners.size() > 0) {
            for (RequestSessionInvalidateListener invalidateListener : invalidateListeners) {
                invalidateListener.onInvalidate(id, username, InvalidateBy.SELF);
            }
        }
        notifyInvalidate(id, username);
    }

    private void notifyInvalidate(String id, String username) {
        if (sessionEventService == null) return;
        try {
            invalidates.put(id, System.currentTimeMillis());
            sessionEventService.send(invalidateTopic, id + SPLIT + username);
        } catch (Exception e) {
            log.error(e.getMessage());
            invalidates.remove(id);
        }
    }

    private void notifyClear(String id) {
        if (sessionEventService == null) return;
        sessionEventService.send(clearTopic, id);
    }

    private T findFromLocal(String id) {
        T session = sessions.get(id);
        if (session == null) return null;
        // 檢查是否超時
        if (!session.valid || session.lastAccessTime + maxInactiveInterval < System.currentTimeMillis()) {
            sessions.remove(id);
            return null;
        }
        return session;
    }

    public static String generateUUIDString(UUID uuid) {
        return (digits(uuid.getMostSignificantBits() >> 32, 8) +
                digits(uuid.getMostSignificantBits() >> 16, 4) +
                digits(uuid.getMostSignificantBits(), 4) +
                digits(uuid.getLeastSignificantBits() >> 48, 4) +
                digits(uuid.getLeastSignificantBits(), 12));
    }

    private static String digits(long val, int digits) {
        long hi = 1L << (digits * 4);
        return Long.toHexString(hi | (val & (hi - 1))).substring(1);
    }
}
