package pers.clare.session;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

public interface SyncSessionStore<T extends SyncSession> {
    void initSchema();

    T find(String id, Long time);

    String findUsername(String id);

    List<SyncSessionId> findAll(String username, String... excludeSessionIds);

    List<SyncSessionId> findAllInvalidate(Long time, Long count);

    void insert(T session) throws SQLException;

    int update(T session);

    int delete(String id);

    int updateLastAccessTime(Collection<T> list);
}
