package pers.clare.session;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

public interface SyncSessionStore<T extends SyncSession> {

    T find(String id, Long time);

    String findUsername(String id);

    List<SyncSessionId> findAll(String username);

    List<SyncSessionId> findAllInvalidate(long time);

    void insert(T session) throws SQLException;

    int update(T session);

    int delete(String id);

    int updateLastAccessTime(Collection<T> list);
}
