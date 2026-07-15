package pers.clare.session.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import pers.clare.session.SyncSessionRequestContextHolder;
import pers.clare.session.configuration.SyncSessionProperties;
import pers.clare.session.constant.SessionMode;
import pers.clare.session.filter.SyncSessionFilter;
import pers.clare.session.store.SyncSessionStore;
import pers.clare.test.session.TokenSession;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class SyncSessionServiceImplRegressionTest {

    @Test
    void batchJobUpdatesTouchedSessionsBeforeDeletingExpiredStoreRows() throws Exception {
        ManualStore store = new ManualStore();
        TestSessionService service = newService(store, Duration.ofMillis(1_000));
        try {
            TokenSession session = service.create(0, "test", "127.0.0.1");
            service.refresh(session);

            session.setLastAccessTime(900);

            service.runBatch(1_100);

            assertTrue(store.contains(session.getId()));
            assertEquals(900, store.lastAccessTime(session.getId()));
            assertTrue(service.sessions.containsKey(session.getId()));
        } finally {
            service.destroy();
        }
    }

    @Test
    void batchUpdateUsesPersistedAccessAgeNotLatestAccessRecency() throws Exception {
        ManualStore store = new ManualStore();
        TestSessionService service = newService(store, Duration.ofMillis(1_000));
        try {
            TokenSession session = service.create(0, "test", "127.0.0.1");
            service.refresh(session);

            session.setLastAccessTime(100);

            assertEquals(1, service.runBatchUpdate(800));
            assertEquals(100, store.lastAccessTime(session.getId()));
            assertEquals(100, session.getLastUpdateAccessTime());
        } finally {
            service.destroy();
        }
    }

    @Test
    void expiredMissingSessionCacheEntriesAreRemovedFromTheRightMap() throws Exception {
        ManualStore store = new ManualStore();
        TestSessionService service = newService(store, Duration.ofMillis(1_000));
        try {
            service.missingSessionCacheMap.put("missing-session", 0L);

            service.runBatchInvalidate(1_001);

            assertFalse(service.missingSessionCacheMap.containsKey("missing-session"));
        } finally {
            service.destroy();
        }
    }

    @Test
    void changedSessionIsInvalidatedWhenStoreUpdateMisses() throws Exception {
        ManualStore store = new ManualStore();
        TestSessionService service = newService(store, Duration.ofMillis(1_000));
        try {
            TokenSession session = service.create(0, "test", "127.0.0.1");
            service.refresh(session);
            store.delete(session.getId());

            session.setCsrfToken("changed");
            service.refresh(session);

            assertFalse(session.isValid());
            assertFalse(service.sessions.containsKey(session.getId()));
            assertTrue(service.missingSessionCacheMap.containsKey(session.getId()));
        } finally {
            service.destroy();
        }
    }

    @Test
    void syncRequestRefreshesSessionEvenWhenResponseIsNotFlushed() throws Exception {
        ManualStore store = new ManualStore();
        TestSessionService service = newService(store, Duration.ofMinutes(30));
        try {
            SyncSessionProperties properties = properties(Duration.ofMinutes(30));
            DefaultSessionIdTransportService transportService = new DefaultSessionIdTransportService();
            transportService.setProperties(properties);

            SyncSessionFilter filter = new SyncSessionFilter();
            filter.setSyncSessionService(service);
            filter.setSessionIdTransportService(transportService);

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/session");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (servletRequest, servletResponse) -> {
                TokenSession session = SyncSessionRequestContextHolder.<TokenSession>get().getSession(true);
                session.setUsername("clare");
            });

            assertEquals(1, store.size());
            assertNotNull(response.getHeader(HttpHeaders.SET_COOKIE));
        } finally {
            service.destroy();
        }
    }

    @Test
    void emptyTransportValueIsIgnored() {
        SyncSessionProperties properties = properties(Duration.ofMinutes(30));
        properties.setMode(SessionMode.Header);
        DefaultSessionIdTransportService transportService = new DefaultSessionIdTransportService();
        transportService.setProperties(properties);

        MockHttpServletRequest emptyHeaderRequest = new MockHttpServletRequest("GET", "/session");
        emptyHeaderRequest.addHeader(properties.getName(), "");

        assertNull(transportService.find(emptyHeaderRequest));
    }

    private static TestSessionService newService(ManualStore store, Duration timeout) {
        SyncSessionProperties properties = properties(timeout);
        TestSessionService service = new TestSessionService();
        service.setProperties(properties);
        service.setStore(store);
        service.afterPropertiesSet();
        return service;
    }

    private static SyncSessionProperties properties(Duration timeout) {
        SyncSessionProperties properties = new SyncSessionProperties();
        properties.setClazz(TokenSession.class);
        properties.setTimeout(timeout);
        properties.setBatchJobDelay(Duration.ofDays(1));
        return properties;
    }

    private static class TestSessionService extends SyncSessionServiceImpl<TokenSession> {
        void runBatch(long now) {
            batchJob(now);
        }

        int runBatchUpdate(long now) {
            return batchUpdate(now);
        }

        long runBatchInvalidate(long now) {
            return batchInvalidate(now);
        }
    }

    private static class ManualStore implements SyncSessionStore<TokenSession> {
        private final Map<String, StoredSession> sessions = new ConcurrentHashMap<>();

        @Override
        public TokenSession newInstance() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
            return TokenSession.class.getConstructor().newInstance();
        }

        @Override
        public TokenSession find(String id, Long time) {
            StoredSession stored = sessions.get(id);
            if (stored == null) return null;
            if (stored.lastAccessTime + stored.maxInactiveInterval <= time) return null;
            TokenSession session = new TokenSession();
            session.setId(stored.id);
            session.setCreateTime(stored.createTime);
            session.setMaxInactiveInterval(stored.maxInactiveInterval);
            session.setLastAccessTime(stored.lastAccessTime);
            session.setLastUpdateAccessTime(stored.lastAccessTime);
            session.setUsername(stored.username);
            session.setAsUpdated();
            return session;
        }

        @Override
        public int deleteAllInvalidate(Long time) {
            int count = 0;
            for (StoredSession stored : List.copyOf(sessions.values())) {
                if (stored.lastAccessTime + stored.maxInactiveInterval <= time
                    && sessions.remove(stored.id, stored)) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public Collection<SyncSessionId> findAllByUsername(String username) {
            return sessions.values().stream()
                    .filter(stored -> username != null && username.equals(stored.username))
                    .map(stored -> new SyncSessionId(stored.id, stored.username))
                    .toList();
        }

        @Override
        public void insert(TokenSession session) {
            StoredSession stored = StoredSession.from(session);
            if (sessions.putIfAbsent(session.getId(), stored) != null) {
                throw new IllegalStateException("Duplicate session " + session.getId());
            }
        }

        @Override
        public int update(TokenSession session) {
            StoredSession stored = sessions.get(session.getId());
            if (stored == null) return 0;
            sessions.put(session.getId(), StoredSession.from(session));
            return 1;
        }

        @Override
        public int delete(String id) {
            return sessions.remove(id) == null ? 0 : 1;
        }

        @Override
        public int[] updateLastAccessTime(List<TokenSession> list) {
            int[] counts = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                TokenSession session = list.get(i);
                StoredSession stored = sessions.get(session.getId());
                if (stored == null) {
                    counts[i] = 0;
                    continue;
                }
                stored.lastAccessTime = session.getLastAccessTime();
                session.setLastUpdateAccessTime(session.getLastAccessTime());
                counts[i] = 1;
            }
            return counts;
        }

        boolean contains(String id) {
            return sessions.containsKey(id);
        }

        long lastAccessTime(String id) {
            StoredSession stored = sessions.get(id);
            return stored == null ? 0 : stored.lastAccessTime;
        }

        int size() {
            return sessions.size();
        }
    }

    private static class StoredSession {
        private final String id;
        private final long createTime;
        private final long maxInactiveInterval;
        private volatile long lastAccessTime;
        private final String username;

        private StoredSession(String id, long createTime, long maxInactiveInterval, long lastAccessTime, String username) {
            this.id = id;
            this.createTime = createTime;
            this.maxInactiveInterval = maxInactiveInterval;
            this.lastAccessTime = lastAccessTime;
            this.username = username;
        }

        private static StoredSession from(TokenSession session) {
            return new StoredSession(
                    session.getId(),
                    session.getCreateTime(),
                    session.getMaxInactiveInterval(),
                    session.getLastAccessTime(),
                    session.getUsername()
            );
        }
    }
}
