package pers.clare.session;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import pers.clare.session.configuration.SyncSessionProperties;
import pers.clare.session.exception.SyncSessionException;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SyncSessionLocalStore<T extends SyncSession> implements SyncSessionStore<T>, DisposableBean {
    private final Logger log = LogManager.getLogger();
    protected final Map<String, T> sessionMap;

    protected final SyncSessionProperties.LocalProperties properties;
    protected final Class<T> sessionClass;

    public SyncSessionLocalStore(SyncSessionProperties.LocalProperties properties, Class<T> sessionClass) {
        this.properties = properties;
        this.sessionClass = sessionClass;
        sessionMap = load();
    }

    public void initSchema() {

    }

    private File getFile() {
        File directory = new File(properties.getPath());
        if (!directory.exists()) {
            directory.mkdirs();
        }
        String filePath = (properties.getPath() + "/" + properties.getFileName()).replaceAll("/+", "/");
        return new File(filePath);
    }


    public void destroy() {
        if (!properties.isPersistence()) return;
        if (sessionMap.size() == 0) return;
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(getFile()))) {
            out.writeObject(sessionMap);
        } catch (IOException e) {
            log.error(e);
        }
    }

    private Map<String, T> load() {
        if (properties.isPersistence()) {
            File file = getFile();
            if (file.exists()) {
                try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
                    Map<String, T> result = (Map<String, T>) in.readObject();
                    return result;
                } catch (Exception e) {
                    log.error(e);
                } finally {
                    file.delete();
                }

            }
        }
        return new ConcurrentHashMap<>();
    }

    @Override
    public T newInstance() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return sessionClass.getConstructor().newInstance();
    }

    public T find(String id, Long time) {
        T session = sessionMap.get(id);
        if (session == null) {
            return null;
        } else {
            if (session.effectiveTime < time) {
                sessionMap.remove(id);
                return null;
            }
            return session;
        }
    }

    public String findUsername(String id) {
        T session = sessionMap.get(id);
        if (session == null) {
            return null;
        } else {
            return session.username;
        }
    }

    @Override
    public Collection<SyncSessionId> findAll(String username, String... excludeSessionIds) {
        List<SyncSessionId> result = new ArrayList<>();
        if (sessionMap.size() == 0) return result;
        Set<String> excludeIds = null;
        if (excludeSessionIds.length > 0) {
            excludeIds = new HashSet<>(excludeSessionIds.length);
            excludeIds.addAll(Arrays.asList(excludeSessionIds));
        }
        for (T value : sessionMap.values()) {
            if (!Objects.equals(username, value.username)) continue;
            if (excludeIds != null && excludeIds.contains(value.id)) continue;
            result.add(value);
        }
        return result;
    }

    public Collection<SyncSessionId> findAllInvalidate(Long time, Long count) {
        List<SyncSessionId> result = new ArrayList<>();
        for (T value : sessionMap.values()) {
            if (value.effectiveTime > time) continue;
            result.add(value);
        }
        return result;
    }

    public void insert(T session) {
        T old = sessionMap.putIfAbsent(session.id, session);
        if (old != null) {
            throw new SyncSessionException(String.format("Duplicate key. %s", session.id));
        }
    }

    public int update(T session) {
        if (session == null) return 0;
        if (sessionMap.containsKey(session.id)) {
            sessionMap.put(session.id, session);
            return 1;
        }
        return 0;
    }

    public int delete(String id) {
        T session = sessionMap.remove(id);
        if (session == null) {
            return 0;
        } else {
            return 1;
        }
    }

    public int updateLastAccessTime(Collection<T> list) {
        int count = 0;
        for (T session : list) {
            T current = sessionMap.get(session.id);
            if (current == null) continue;
            current.lastAccessTime = session.lastAccessTime;
            current.effectiveTime = session.effectiveTime;
            count++;
        }
        return count;
    }
}
