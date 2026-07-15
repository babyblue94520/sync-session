package pers.clare.session;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import pers.clare.session.configuration.SyncSessionProperties;
import pers.clare.session.event.SyncSessionEventService;
import pers.clare.session.service.SyncSessionService;
import pers.clare.test.ApplicationTest;
import pers.clare.test.session.SyncSessionEventServiceImpl;
import pers.clare.urlrequest.URLRequest;
import pers.clare.urlrequest.URLRequestMethod;
import pers.clare.urlrequest.URLResponse;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@Slf4j
@TestInstance(PER_CLASS)
class ClusterInvalidateSmokeTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private ConfigurableApplicationContext h2Context;
    private final List<ConfigurableApplicationContext> applications = new ArrayList<>();
    private final List<Integer> ports = new ArrayList<>();
    private SyncSessionService<?> syncSessionService;
    private SyncSessionEventService syncSessionEventService;
    private DataSource dataSource;
    private String tableName;
    private EventProbe eventProbe;

    @BeforeAll
    @SuppressWarnings("unchecked")
    void beforeAll() {
        int h2Port = TestPorts.freePort();
        h2Context = TestH2Support.start(h2Port, "MYSQL", "cluster_smoke");
        SyncSessionEventServiceImpl.reset();
        for (int i = 0; i < 3; i++) {
            ConfigurableApplicationContext context = SpringApplication.run(ApplicationTest.class,
                    "--server.port=0",
                    "--spring.profiles.active=h2",
                    "--h2.port=" + h2Port,
                    "--h2.mode=MYSQL",
                    "--h2.dbName=cluster_smoke",
                    "--listen=true",
                    "--sync-session.timeout=30m"
            );
            applications.add(context);
            ports.add(Integer.parseInt(context.getEnvironment().getProperty("local.server.port")));
        }
        syncSessionService = (SyncSessionService<?>) applications.get(0).getBean(SyncSessionService.class);
        syncSessionEventService = applications.get(0).getBean(SyncSessionEventService.class);
        dataSource = applications.get(0).getBean(DataSource.class);
        tableName = applications.get(0).getBean(SyncSessionProperties.class).getDs().getTableName();
        eventProbe = new EventProbe();
        syncSessionEventService.addListener(eventProbe::accept);
    }

    @BeforeEach
    void beforeEach() {
        eventProbe.reset();
    }

    @AfterAll
    void afterAll() {
        for (ConfigurableApplicationContext application : applications) {
            application.close();
        }
        if (h2Context != null) {
            h2Context.close();
        }
        SyncSessionEventServiceImpl.shutdown();
    }

    @Test
    void invalidatePropagatesAcrossNodes() throws Exception {
        CookieManager client = new CookieManager();
        CreatedSession createdSession = createSession(client, "cluster-user");
        String token = createdSession.token();
        assertNotNull(token);

        String sessionId = createdSession.sessionId();
        assertNotNull(sessionId);
        delete(client, ports.get(1));

        boolean received = eventProbe.await(Set.of(sessionId), Duration.ofSeconds(15));
        assertTrue(received, () -> "event not received for sessionId=" + sessionId
                                   + ", events=" + eventProbe.snapshot()
                                   + ", token@node0=" + getToken(client, ports.get(0))
                                   + ", token@node1=" + getToken(client, ports.get(1))
                                   + ", token@node2=" + getToken(client, ports.get(2)));
        assertTrue(TestWait.until(Duration.ofSeconds(15), Duration.ofMillis(100), () -> {
            String current = getToken(client, ports.get(2));
            return current == null || current.isEmpty();
        }));
    }

    @Test
    void invalidateByUsernamePropagatesAcrossNodes() throws Exception {
        int publisherPort = ports.get(0);
        String username = "shared-user";
        CookieManager keeper = new CookieManager();
        CreatedSession keeperSession = createSession(keeper, username, publisherPort);
        String keepToken = keeperSession.token();

        String keepSessionId = keeperSession.sessionId();
        assertNotNull(keepToken);
        assertNotNull(keepSessionId);
        List<VictimSession> victims = new ArrayList<>();
        for (int port : ports) {
            CookieManager victim = new CookieManager();
            CreatedSession victimSession = createSession(victim, username, port);
            String victimSessionId = victimSession.sessionId();
            victims.add(new VictimSession(victim, port, victimSessionId));
        }

        syncSessionService.invalidateByUsername(username, keepSessionId);
        assertFalse(eventProbe.snapshot().contains(keepSessionId));
        for (VictimSession victim : victims) {
            assertTrue(TestWait.until(Duration.ofSeconds(15), Duration.ofMillis(100), () -> {
                String current = getToken(victim.cookieManager(), victim.port());
                return current == null || current.isEmpty();
            }), () -> "victim session still available, sessionId=" + victim.sessionId()
                      + ", publisherPort=" + publisherPort
                      + ", originPort=" + victim.port()
                      + ", token@publisher=" + getToken(victim.cookieManager(), publisherPort)
                      + ", token@origin=" + getToken(victim.cookieManager(), victim.port())
                      + ", events=" + eventProbe.snapshot());
        }
        assertEquals(keepToken, getToken(keeper, publisherPort));
    }

    private CreatedSession createSession(CookieManager cookieManager, String username) throws Exception {
        return createSession(cookieManager, username, ports.get(0));
    }

    private CreatedSession createSession(CookieManager cookieManager, String username, int port) throws Exception {
        URLResponse<String> response = URLRequest.build(url(port, ""))
                .cookieManager(cookieManager)
                .params(Map.of("username", username, "includeSessionId", true))
                .method(URLRequestMethod.POST)
                .go();
        CreatedSession createdSession = parseCreatedSession(cookieManager, port, response.getBody());
        assertTrue(sessionExistsInStore(createdSession.sessionId()),
                () -> "created session is not persisted, sessionId=" + createdSession.sessionId() + ", port=" + port);
        return createdSession;
    }

    private CreatedSession parseCreatedSession(CookieManager cookieManager, int port, String body) {
        int separator = body == null ? -1 : body.indexOf(':');
        if (separator < 1) {
            return new CreatedSession(extractSessionId(cookieManager), body);
        }
        String sessionId = body.substring(0, separator);
        ensureCookie(cookieManager, port, sessionId);
        return new CreatedSession(sessionId, body.substring(separator + 1));
    }

    private void ensureCookie(CookieManager cookieManager, int port, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        boolean exists = cookieManager.getCookieStore().getCookies().stream()
                .anyMatch(cookie -> "SSESSIONID".equalsIgnoreCase(cookie.getName()) && sessionId.equals(cookie.getValue()));
        if (exists) {
            return;
        }
        HttpCookie cookie = new HttpCookie("SSESSIONID", sessionId);
        cookie.setPath("/");
        cookieManager.getCookieStore().add(URI.create(url(port, "")), cookie);
    }

    private String getToken(CookieManager cookieManager, int port) {
        return URLRequest.build(url(port, "/token"))
                .cookieManager(cookieManager)
                .get()
                .getBody();
    }

    private void delete(CookieManager cookieManager, int port) {
        String sessionId = extractSessionId(cookieManager);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(port, "")))
                .DELETE();
        if (sessionId != null && !sessionId.isBlank()) {
            builder.header("Cookie", "SSESSIONID=" + sessionId);
        }
        try {
            HTTP_CLIENT.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String extractSessionId(CookieManager cookieManager) {
        return cookieManager.getCookieStore().getCookies().stream()
                .filter(cookie -> "SSESSIONID".equalsIgnoreCase(cookie.getName()))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private String url(int port, String path) {
        return "http://localhost:" + port + "/session" + path;
    }

    private boolean sessionExistsInStore(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("select 1 from " + tableName + " where id=?")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static class EventProbe {
        private final Set<String> invalidated = java.util.concurrent.ConcurrentHashMap.newKeySet();

        void reset() {
            invalidated.clear();
        }

        void accept(String body) {
            if (body == null) {
                return;
            }
            String[] parts = body.split("\n");
            if (parts.length == 2 && "INVALIDATE".equals(parts[0]) && parts[1] != null && !parts[1].isBlank()) {
                invalidated.add(parts[1]);
            }
        }

        boolean await(Set<String> ids, Duration timeout) throws InterruptedException {
            return TestWait.until(timeout, Duration.ofMillis(100), () -> invalidated.containsAll(ids));
        }

        Set<String> snapshot() {
            return Set.copyOf(invalidated);
        }
    }

    private record CreatedSession(String sessionId, String token) {
    }

    private record VictimSession(CookieManager cookieManager, int port, String sessionId) {
    }
}
