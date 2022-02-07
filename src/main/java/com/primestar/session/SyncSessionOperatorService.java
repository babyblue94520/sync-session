package com.primestar.session;

import com.primestar.session.listener.SyncSessionInvalidateListener;

public interface SyncSessionOperatorService<T extends SyncSession> {
    SyncSessionProperties getProperties();

    T find(String id);

    void invalidate(T session);

    void invalidate(String id);

    void invalidateByUsername(String username, String... excludeSessionIds);

    SyncSessionInvalidateListener addInvalidateListeners(SyncSessionInvalidateListener listener);

}
