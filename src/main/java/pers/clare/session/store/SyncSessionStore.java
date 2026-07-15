package pers.clare.session.store;

import pers.clare.session.service.SyncSessionId;
import pers.clare.session.service.SyncSession;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

public interface SyncSessionStore<T extends SyncSession> {

    T newInstance() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException;

    T find(String id, Long time);

    /**
     * Delete expired sessions directly from storage.
     *
     * @param time current time in millis
     * @return deleted count
     */
    int deleteAllInvalidate(Long time);

    Collection<SyncSessionId> findAllByUsername(String username);

    void insert(T session) throws SQLException;

    int update(T session);

    int delete(String id);

    int[] updateLastAccessTime(List<T> list);
}
