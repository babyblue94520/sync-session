package pers.clare.session;

import pers.clare.session.listener.RequestSessionInvalidateListener;

public interface SyncSessionService<T extends SyncSession> {
    SyncSessionProperties getProperties();

    T find(String id);

    T create(long accessTime, String userAgent, String ip);

    int update(T session);

    void invalidate(T session);

    void invalidate(String id);

    void invalidateByUsername(String username);

    RequestSessionInvalidateListener addInvalidateListeners(RequestSessionInvalidateListener listener);

}
