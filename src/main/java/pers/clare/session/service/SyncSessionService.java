package pers.clare.session.service;

public interface SyncSessionService<T extends SyncSession> extends SyncSessionInvalidateService {
    T find(String sessionId);

    T create(long accessTime, String userAgent, String ip);

    void invalidate(T session);

    void refresh(T session);

    boolean keepalive(String sessionId);
}
