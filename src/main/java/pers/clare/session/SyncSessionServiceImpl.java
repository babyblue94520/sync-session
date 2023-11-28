package pers.clare.session;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import pers.clare.session.event.SyncSessionEventService;
import pers.clare.session.exception.SyncSessionException;
import pers.clare.session.util.SessionUtil;
import pers.clare.session.configuration.SyncSessionProperties;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SyncSessionServiceImpl<T extends SyncSession> extends SyncSessionOperatorServiceImpl<T> implements SyncSessionService<T>, InitializingBean, DisposableBean {

    private final Logger log = LogManager.getLogger();

    // retry create session count
    public final int maxRetryInsert = 5;

    // refresh session to storage interval
    private long updateInterval;

    private ScheduledExecutorService executor;

    public SyncSessionServiceImpl(
            SyncSessionProperties properties
            , SyncSessionStore<T> store
    ) {
        super(properties, store);
    }

    public SyncSessionServiceImpl(
            SyncSessionProperties properties
            , SyncSessionStore<T> store
            , SyncSessionEventService sessionEventService
    ) {
        super(properties, store, sessionEventService);
    }

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        store.initSchema();

        // update session to storage interval
        updateInterval = maxInactiveInterval / 2;

        // update session job interval
        long delay = maxInactiveInterval / 10;
        if (delay < 1000) {
            delay = 1000;
        }
        // check session status scheduler
        executor = Executors.newScheduledThreadPool(1);
        executor.scheduleWithFixedDelay(this::batchUpdate, delay, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        if (executor == null) return;
        executor.shutdown();
        log.info("{} executor shutdown...", "Session");
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                throw new InterruptedException("Session executor did not shutdown gracefully within 30 seconds. Proceeding with forceful shutdown.");
            }
            log.info("Session executor shutdown complete.");
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }

    public T create(
            long accessTime
            , String userAgent
            , String ip
    ) {
        try {
            T session = store.newInstance();
            session.createTime = accessTime;
            session.maxInactiveInterval = maxInactiveInterval;
            session.lastAccessTime = accessTime;
            session.effectiveTime = accessTime + maxInactiveInterval;
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
            clear(session);
        }
        return count;
    }

    public boolean keepalive(String id) {
        T session = find(id);
        if (session == null) return false;
        session.setLastAccessTime(System.currentTimeMillis() + maxInactiveInterval);
        return true;
    }

    private T doInsert(T session, int count) {
        try {
            session.id = SessionUtil.generateId();
            store.insert(session);
            sessions.put(session.id, session);
            return session;
        } catch (SyncSessionException e) {
            SQLException sqlException = e.getSqlException();
            if (sqlException instanceof SQLIntegrityConstraintViolationException
                && sqlException.getErrorCode() == 1062
                && count < maxRetryInsert
            ) {
                // retry where uuid is existed
                return doInsert(session, count + 1);
            }
            throw e;
        } catch (Exception e) {
            throw new SyncSessionException(e);
        }
    }

    private void batchUpdate() {
        log.debug("batchUpdate start...");
        try {
            long t = System.currentTimeMillis();
            long now = t;
            int originSize = sessions.size();
            if (originSize > 0) {
                List<T> updates = new ArrayList<>(originSize);
                Iterator<T> iterator = sessions.values().iterator();
                T session;
                while (iterator.hasNext()) {
                    session = iterator.next();
                    // update active session
                    if (session.lastAccessTime == session.lastUpdateAccessTime) continue;
                    if (session.lastAccessTime + updateInterval > now) {
                        session.lastUpdateAccessTime = session.lastAccessTime;
                        updates.add(session);
                    }
                }

                t = System.currentTimeMillis();
                int count = store.updateLastAccessTime(updates);
                log.debug("update session:{} real:{} {}ms", updates.size(), count, System.currentTimeMillis() - t);
            }

            long invalidateCount = batchInvalidate(now);
            log.debug("invalidate session:{} {}ms", invalidateCount, System.currentTimeMillis() - t);
            log.debug("batchUpdate {}>{} {}ms", originSize, sessions.size(), (System.currentTimeMillis() - now));
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    public long batchInvalidate(Long time) {
        long count = 0;
        Collection<SyncSessionId> ids;
        while ((ids = store.findAllInvalidate(time, properties.getBatchInvalidateCount())).size() > 0) {
            count += batchInvalidate(ids);
        }
        return count;
    }
}
