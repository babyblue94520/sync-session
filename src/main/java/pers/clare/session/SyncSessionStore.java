package pers.clare.session;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Collection;

public interface SyncSessionStore<T extends SyncSession> {
    void initSchema();

    T newInstance() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException;

    T find(String id, Long time);

    String findUsername(String id);

    Collection<SyncSessionId> findAll(String username, String... excludeSessionIds);

    Collection<SyncSessionId> findAllInvalidate(Long time, Long count);

    void insert(T session) throws SQLException;

    int update(T session);

    int delete(String id);

    int updateLastAccessTime(Collection<T> list);
}
