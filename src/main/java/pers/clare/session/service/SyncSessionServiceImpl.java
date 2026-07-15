package pers.clare.session.service;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import pers.clare.session.configuration.SyncSessionProperties;
import pers.clare.session.constant.EventType;
import pers.clare.session.exception.SyncSessionException;
import pers.clare.session.util.SessionUtil;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
public class SyncSessionServiceImpl<T extends SyncSession> extends SyncSessionInvalidateServiceImpl<T> implements SyncSessionService<T>, InitializingBean, DisposableBean {

    protected final ConcurrentMap<String, T> sessions = new ConcurrentHashMap<>();
    protected final ConcurrentMap<String, Long> missingSessionCacheMap = new ConcurrentHashMap<>();
    protected final ConcurrentMap<String, Long> clearEventEchoGuardMap = new ConcurrentHashMap<>();
    protected final ConcurrentMap<String, Long> invalidateEventEchoGuardMap = new ConcurrentHashMap<>();

    @Setter(onMethod_ = {@Autowired})
    protected SyncSessionProperties properties;

    // refresh session to storage interval
    private long updateInterval;

    private long maxInactiveInterval;

    private ScheduledExecutorService executor;

    @Override
    public void afterPropertiesSet() {
        maxInactiveInterval = properties.getTimeout().toMillis();

        // update session to storage interval
        updateInterval = maxInactiveInterval / 2;

        // update session job interval
        long delay = properties.getBatchJobDelay().toMillis();
        if (delay < 10000) {
            delay = 10000;
        }
        // check session status scheduler
        executor = Executors.newScheduledThreadPool(1);
        executor.scheduleWithFixedDelay(this::batchJob, delay, delay, TimeUnit.MILLISECONDS);

        if (sessionEventService != null) {
            sessionEventService.addListener(this::eventHandler);
        }
    }

    @Override
    public void destroy() throws InterruptedException {
        if (executor == null) return;
        executor.shutdown();
        log.debug("{} executor shutdown...", "Session");
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            log.error("Session executor did not shutdown gracefully within 30 seconds. Proceeding with forceful shutdown.");
        }
        log.debug("Session executor shutdown complete.");
    }

    private boolean isAvailable() {
        return sessionEventService == null || sessionEventService.isAvailable();
    }

    private void init(T session) {
        if (session == null) return;
        session.valid = true;
        session.service = this;
    }

    public T find(String id) {
        if (id == null) return null;
        if (missingSessionCacheMap.containsKey(id)) {
            log.debug("session {} not exists", id);
            return null;
        }
        T session;
        if (isAvailable()) {
            session = findFromLocal(id);
            if (session == null) {
                session = sessions.computeIfAbsent(id, this::findFromStore);
            }
        } else {
            session = findFromStore(id);
        }
        if (session == null) {
            missingSessionCacheMap.put(id, System.currentTimeMillis());
        } else {
            init(session);
        }
        return session;
    }

    private T findFromLocal(String id) {
        T session = sessions.get(id);
        if (session == null) return null;
        init(session);
        if (session.valid && (session.lastAccessTime + session.maxInactiveInterval) > System.currentTimeMillis()) {
            return session;
        }
        sessions.remove(id);
        return null;
    }

    private T findFromStore(String id) {
        return store.find(id, System.currentTimeMillis());
    }

    public T create(
            long accessTime
            , String userAgent
            , String ip
    ) {
        try {
            T session = store.newInstance();
            session.setAsNew();
            session.createTime = accessTime;
            session.maxInactiveInterval = maxInactiveInterval;
            session.lastAccessTime = accessTime;
            session.lastUpdateAccessTime = accessTime;
            session.userAgent = userAgent;
            session.ip = ip;

            session.id = SessionUtil.generateId();
            init(session);
            sessions.put(session.id, session);
            return session;
        } catch (Exception e) {
            throw new SyncSessionException(e);
        }
    }

    public void refresh(T session) {
        if (session == null) return;
        if (session.isNew) {
            doInsert(session, 0);
            session.setAsUpdated();
        } else if (session.changed) {
            int count = store.update(session);
            if (count > 0) {
                clearEvent(session.id);
                session.setAsUpdated();
            } else {
                invalidateHandle(session.id);
            }
        }
    }

    public boolean keepalive(String id) {
        T session = find(id);
        if (session == null) return false;
        session.setLastAccessTime(System.currentTimeMillis());
        return true;
    }

    protected T doInsert(T session, int count) {
        try {
            store.insert(session);
            return session;
        } catch (SyncSessionException e) {
            SQLException sqlException = e.getSqlException();
            if (isDuplicateKey(sqlException) && count < properties.getMaxRetryInsert()) {
                rotateSessionId(session);
                return doInsert(session, count + 1);
            }
            throw e;
        } catch (Exception e) {
            throw new SyncSessionException(e);
        }
    }

    private boolean isDuplicateKey(SQLException sqlException) {
        if (sqlException == null) return false;
        if (sqlException instanceof SQLIntegrityConstraintViolationException) {
            return true;
        }
        String sqlState = sqlException.getSQLState();
        return "23505".equals(sqlState) || sqlException.getErrorCode() == 1062;
    }

    private void rotateSessionId(T session) {
        String oldId = session.getId();
        String newId = SessionUtil.generateId();
        session.id = newId;
        if (oldId != null) {
            sessions.remove(oldId, session);
        }
        sessions.put(newId, session);
    }

    public void invalidate(SyncSession session) {
        if (session == null) return;
        session.valid = false;
        invalidate(session.id);
    }

    public void invalidate(String id) {
        if (id == null) return;
        invalidateHandle(id);
        int count = store.delete(id);
        if (count > 0) {
            invalidateEvent(id);
        }
    }

    protected void batchJob() {
        try {
            batchJob(System.currentTimeMillis());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    protected void batchJob(long now) {
        long t = System.currentTimeMillis();
        int updateCount = batchUpdate(now);
        log.debug("updated: {} [{}ms]", updateCount, (System.currentTimeMillis() - t));
        t = System.currentTimeMillis();
        long invalidateCount = batchInvalidate(now);
        log.debug("invalidated: {} [{}ms]", invalidateCount, System.currentTimeMillis() - t);
    }

    protected long batchInvalidate(long now) {
        long validTime = now - maxInactiveInterval;
        clearExpired(missingSessionCacheMap, validTime);
        clearExpired(clearEventEchoGuardMap, validTime);
        clearExpired(invalidateEventEchoGuardMap, validTime);
        for (var entry : sessions.entrySet()) {
            T session = entry.getValue();
            if ((session.lastAccessTime + session.maxInactiveInterval) <= now) {
                sessions.remove(session.id);
            }
        }
        return store.deleteAllInvalidate(now);
    }

    protected int batchUpdate(long now) {
        int originSize = sessions.size();
        if (originSize == 0) return originSize;

        List<T> updates = new ArrayList<>(originSize);
        long needUpdateTime = now - updateInterval;
        for (var entry : sessions.entrySet()) {
            T session = entry.getValue();
            long lastAccessTime = session.lastAccessTime;
            if ((lastAccessTime + session.maxInactiveInterval) <= now) continue;
            if (lastAccessTime == session.lastUpdateAccessTime) continue;
            if (session.lastUpdateAccessTime <= needUpdateTime) {
                updates.add(session);
            }
        }

        int[] counts = store.updateLastAccessTime(updates);
        return processUpdateResults(updates, counts);
    }

    private void clearExpired(ConcurrentMap<String, Long> map, long validTime) {
        for (var entry : map.entrySet()) {
            if (entry.getValue() <= validTime) {
                map.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private int processUpdateResults(List<T> updates, int[] counts) {
        if (updates == null || updates.isEmpty() || counts == null || counts.length == 0) return 0;
        int count = 0;
        int length = Math.min(updates.size(), counts.length);
        for (int i = 0; i < length; i++) {
            int result = counts[i];
            if (result == 0) {
                sessions.remove(updates.get(i).id);
            } else {
                count++;
            }
        }
        return count;
    }


    private void clearEvent(String id) {
        publishEvent(EventType.CLEAR, id);
    }

    private void invalidateEvent(String id) {
        publishEvent(EventType.INVALIDATE, id);
    }

    private void publishEvent(String type, String id) {
        if (sessionEventService == null) return;
        if (type.equals(EventType.INVALIDATE)) {
            invalidateEventEchoGuardMap.put(id, System.currentTimeMillis());
        } else if (type.equals(EventType.CLEAR)) {
            clearEventEchoGuardMap.put(id, System.currentTimeMillis());
        } else {
            return;
        }
        sessionEventService.send(type + SPLIT + id);
    }

    private void eventHandler(String body) {
        log.debug("event body: {}", body);
        String[] array = body.split(SPLIT);
        if (array.length != 2) return;
        String type = array[0];
        String id = array[1];
        if (type.equals(EventType.INVALIDATE)) {
            if (invalidateEventEchoGuardMap.remove(id) != null) return;
            invalidateHandle(id);
        } else if (type.equals(EventType.CLEAR)) {
            if (clearEventEchoGuardMap.remove(id) != null) return;
            clearHandle(id);
        }
    }

    private void clearHandle(String id) {
        sessions.remove(id);
    }

    private void invalidateHandle(String id) {
        missingSessionCacheMap.put(id, System.currentTimeMillis());
        T session = sessions.remove(id);
        if (session != null) {
            session.valid = false;
        }
    }
}
