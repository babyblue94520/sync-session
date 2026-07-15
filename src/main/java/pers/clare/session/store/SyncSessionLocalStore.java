package pers.clare.session.store;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import pers.clare.session.configuration.SyncSessionProperties;
import pers.clare.session.exception.SyncSessionException;
import pers.clare.session.service.SyncSession;
import pers.clare.session.service.SyncSessionId;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SyncSessionLocalStore<T extends SyncSession> implements SyncSessionStore<T>, InitializingBean, DisposableBean {
    protected Map<String, T> sessionMap = new ConcurrentHashMap<>();

    protected Class<T> sessionClass;

    @Setter(onMethod_ = {@Autowired})
    protected SyncSessionProperties properties;

    @Override
    public void afterPropertiesSet() throws Exception {
        this.sessionClass = (Class<T>) properties.getClazz();
        this.sessionMap.putAll(load());
    }

    private File getFile() {
        File directory = new File(properties.getLocal().getPath());
        if (!directory.exists()) {
            directory.mkdirs();
        }
        String filePath = (properties.getLocal().getPath() + '/' + properties.getLocal().getFileName()).replaceAll("/+", "/");
        return new File(filePath);
    }

    public void destroy() {
        if (!properties.getLocal().isPersistence()) return;
        if (sessionMap.isEmpty()) return;
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(getFile()))) {
            out.writeObject(sessionMap);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private Map<String, T> load() {
        if (properties.getLocal().isPersistence()) {
            File file = getFile();
            if (file.exists()) {
                try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
                    return (Map<String, T>) in.readObject();
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
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
        }
        if (session.getLastAccessTime() + session.getMaxInactiveInterval() < time) {
            sessionMap.remove(id);
            return null;
        }
        return session;
    }

    @Override
    public int deleteAllInvalidate(Long time) {
        if (time == null) return 0;
        int count = 0;
        for (Map.Entry<String, T> entry : sessionMap.entrySet()) {
            T value = entry.getValue();
            if (value == null) continue;
            if (value.getLastAccessTime() + value.getMaxInactiveInterval() > time) continue;
            if (sessionMap.remove(entry.getKey(), value)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public Collection<SyncSessionId> findAllByUsername(String username) {
        if (username == null) return List.of();
        List<SyncSessionId> ids = new ArrayList<>();
        for (var entry : sessionMap.entrySet()) {
            T value = entry.getValue();
            if (value == null) continue;
            if (!Objects.equals(username, value.getUsername())) continue;
            ids.add(new SyncSessionId(value.getId(), value.getUsername()));
        }
        return ids;
    }

    public void insert(T session) {
        T old = sessionMap.putIfAbsent(session.getId(), session);
        if (old != null) {
            throw new SyncSessionException(String.format("Duplicate key. %s", session.getId()));
        }
    }

    public int update(T session) {
        if (session == null) return 0;
        if (sessionMap.containsKey(session.getId())) {
            sessionMap.put(session.getId(), session);
            return 1;
        }
        return 0;
    }

    public int delete(String id) {
        T session = sessionMap.remove(id);
        if (session == null) {
            return 0;
        }
        return 1;
    }

    public int[] updateLastAccessTime(List<T> list) {
        int[] counts = new int[list.size()];
        int index = 0;
        for (T session : list) {
            T current = sessionMap.get(session.getId());
            if (current == null) {
                counts[index++] = 0;
                continue;
            }
            current.setLastUpdateAccessTime(session.getLastAccessTime());
            counts[index++] = 1;
        }
        return counts;
    }
}
