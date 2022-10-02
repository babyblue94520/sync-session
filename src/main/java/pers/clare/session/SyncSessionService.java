package pers.clare.session;

public interface SyncSessionService<T extends SyncSession> extends SyncSessionOperatorService<T> {
    T create(long accessTime, String userAgent, String ip);

    int update(T session);

    boolean keepalive(String sessionId);
}
