package pers.clare.session;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.ConcurrentReferenceHashMap;
import pers.clare.session.constant.EventType;
import pers.clare.session.constant.InvalidateBy;
import pers.clare.session.listener.SyncSessionInvalidateListener;
import pers.clare.session.util.SessionUtil;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public class SyncSessionOperatorServiceImpl<T extends SyncSession> implements SyncSessionOperatorService<T>, InitializingBean {
    private static final Logger log = LogManager.getLogger();

    public static final String SPLIT = "\n";

    // session id length
    public static final int ID_LENGTH = 32;

    // local session cache
    protected final ConcurrentMap<String, T> sessions = new ConcurrentHashMap<>();

    // avoid repeated operations
    protected final ConcurrentMap<String, Long> invalidates = new ConcurrentReferenceHashMap<>();

    protected final List<SyncSessionInvalidateListener> invalidateListeners = new CopyOnWriteArrayList<>();

    // session class
    protected final Class<T> sessionClass;

    // session storage
    protected final SyncSessionStore<T> store;

    protected Function<String, ? extends T> findFromStore;

    protected final SyncSessionProperties properties;

    protected final SyncSessionEventService sessionEventService;

    // session max inactive interval
    protected long maxInactiveInterval;

    @SuppressWarnings("unchecked")
    public SyncSessionOperatorServiceImpl(
            SyncSessionProperties properties
            , DataSource dataSource
            , SyncSessionEventService sessionEventService
    ) {
        this.sessionClass = (Class<T>) SessionUtil.getSessionClass(this.getClass());
        this.properties = properties;
        this.sessionEventService = sessionEventService;
        this.store = new SyncSessionStoreImpl<>(dataSource, this.sessionClass);
    }

    @Override
    public void afterPropertiesSet() {
        findFromStore = this::findFromStore;
        maxInactiveInterval = properties.getTimeout().toMillis();

        // listener session invalidated event
        if (sessionEventService != null) {
            sessionEventService.addListener(this::eventHandler);
        }
    }

    public SyncSessionProperties getProperties() {
        return properties;
    }

    public T find(String id) {
        if (id == null || id.length() != ID_LENGTH) return null;
        T session = findFromLocal(id);
        if (session != null) return session;
        return sessions.computeIfAbsent(id, findFromStore);
    }

    protected T findFromLocal(String id) {
        T session = sessions.get(id);
        if (session == null) return null;
        if (session.valid
                && session.effectiveTime > System.currentTimeMillis()
                && (sessionEventService == null || sessionEventService.isAvailable())
        ) {
            return session;
        }
        sessions.remove(id);
        return null;
    }

    protected T findFromStore(String id) {
        return store.find(id, System.currentTimeMillis());
    }

    public void invalidate(T session) {
        if (session == null) return;
        invalidate(session.id);
    }

    public void invalidate(String id) {
        if (id == null || id.length() != ID_LENGTH) return;
        doInvalidate(id);
    }

    public void invalidateByUsername(String username, String... excludeSessionIds) {
        batchInvalidate(store.findAll(username, excludeSessionIds));
    }


    protected void doInvalidate(String id) {
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

    protected void triggerInvalidateEvent(String id, String username) {
        if (invalidateListeners.size() > 0) {
            for (SyncSessionInvalidateListener invalidateListener : invalidateListeners) {
                invalidateListener.onInvalidate(id, username, InvalidateBy.SELF);
            }
        }
        if (sessionEventService == null) return;
        try {
            invalidates.put(id, System.currentTimeMillis());
            send(EventType.INVALIDATE, id, username);
        } catch (Exception e) {
            invalidates.remove(id);
            log.error(e.getMessage());
        }
    }

    private void invalidateHandler(String id, String username) {
        if (invalidates.remove(id) != null) return;
        sessions.remove(id);
        if (invalidateListeners.size() > 0) {
            for (SyncSessionInvalidateListener invalidateListener : invalidateListeners) {
                invalidateListener.onInvalidate(id, username, InvalidateBy.NOTICE);
            }
        }
    }

    public SyncSessionInvalidateListener addInvalidateListeners(SyncSessionInvalidateListener listener) {
        if (listener == null) return null;
        invalidateListeners.add(listener);
        return listener;
    }

    protected long batchInvalidate(List<SyncSessionId> ids) {
        if (ids.size() == 0) return 0;
        long count = 0;
        String id;
        for (SyncSessionId syncSessionId : ids) {
            try {
                id = syncSessionId.getId();
                sessions.remove(id);
                if (store.delete(id) == 0) continue;
                count++;
                triggerInvalidateEvent(id, syncSessionId.getUsername());
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        return count;
    }


    public void clear(T session) {
        sessions.remove(session.id);
        if (sessionEventService == null) return;
        session.refresh.block();
        send(EventType.CLEAR, session.id, session.username);
    }

    private void clearHandler(String id, String username) {
        T session = sessions.get(id);
        if (session == null) return;
        // avoid repeated operations
        if (session.refresh.isBlock()) return;
        log.debug("do clear {}", id);
        sessions.remove(id);
    }

    private void send(String type, String id, String username) {
        if (sessionEventService == null) return;
        sessionEventService.send(type + SPLIT + id + SPLIT + username);
    }

    private void eventHandler(String body) {
        log.debug("event body:{}", body);
        String[] array = body.split(SPLIT);
        if (array.length != 3) return;
        String type = array[0];
        String id = array[1];
        String username = array[2];
        switch (type) {
            case EventType.INVALIDATE:
                invalidateHandler(id, username);
                break;
            case EventType.CLEAR:
                clearHandler(id, username);
                break;
        }
    }
}
