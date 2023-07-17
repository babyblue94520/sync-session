package pers.clare.session;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import pers.clare.h2.H2Application;
import pers.clare.session.constant.InvalidateBy;
import pers.clare.test.ApplicationTest2;
import pers.clare.test.config.SessionConfig;
import pers.clare.test.config.SessionListenerConfig;
import pers.clare.test.session.SyncSessionEventServiceImpl;
import pers.clare.urlrequest.URLRequest;
import pers.clare.urlrequest.URLRequestMethod;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;


@Sql(scripts = {"/schema/schema.sql"})
@DisplayName("SessionTest")
@TestInstance(PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import({SessionConfig.class, SessionListenerConfig.class})
@SpringBootTest(args = {"--listen=true"})
class SessionTest {
    private static final List<String> ports = new ArrayList<>();

    private static final List<ApplicationContext> applications = new ArrayList<>();


    static {
        SyncSessionEventServiceImpl.reset();
        applications.add(SpringApplication.run(H2Application.class
                , "--spring.profiles.active=h2server"
                , "--h2.port=9090"
        ));
        for (int i = 0; i < 3; i++) {
            ports.add(String.valueOf(9000 + i));
        }
    }

    private final CookieManager cookieManager = new CookieManager();

    @Autowired
    private SyncSessionProperties syncSessionProperties;

    @Autowired
    private SyncSessionService<?> syncSessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    @BeforeAll
    void before() {
        for (String port : ports) {
            applications.add(SpringApplication.run(ApplicationTest2.class
                    , "--server.port=" + port
                    , "--h2.port=9090"
                    , "--listen=true"
            ));
        }
    }

    @BeforeEach
    void beforeEach() {
        jdbcTemplate.execute("truncate table log");
    }

    @AfterAll
    void after() {
        for (ApplicationContext application : applications) {
            SpringApplication.exit(application);
        }
    }

    @Test
    @Order(1)
    void create() {
        String token = createSession();
        assertNotEquals("", token);
        verifyToken(token);
    }

    @Test
    @Order(2)
    void token() {
        String token = getToken();
        assertNotEquals("", token);
        verifyToken(token);
    }

    @Test
    @Order(3)
    void resetToken() throws InterruptedException {
        create();
        String token = result(getRandomPort(), "/token/reset", URLRequestMethod.POST);
        assertNotEquals("", token);
        Thread.sleep(500);
        verifyToken(token);
    }

    @Test
    @Order(4)
    void alive() throws InterruptedException {
        String token = getToken();
        assertNotEquals("", token);

        long timeout = syncSessionProperties.getTimeout().toMillis() * 2;
        for (int i = 0; i < timeout; i += 1000) {
            Thread.sleep(1000);
            assertEquals(token, URLRequest.build(toUrl(ports.get(0), "/token"))
                    .cookieManager(cookieManager)
                    .get()
                    .getBody()
            );
        }
        verifyToken(token);
    }

    @Test
    @Order(5)
    void ping() throws InterruptedException {
        long timeout = syncSessionProperties.getTimeout().toMillis() * 2;
        for (int i = 0; i < timeout; i += 1000) {
            URLRequest.build(toUrl(ports.get(0), "/ping"))
                    .cookieManager(cookieManager)
                    .post();
            Thread.sleep(1000);
        }
        verifyToken("");
        verifyListener();
    }

    @Test
    @Order(6)
    void invalidate() throws InterruptedException {
        // create
        String token = createSession();
        assertNotEquals("", token);
        verifyToken(token);

        // invalidate
        URLRequest.build(toUrl(getRandomPort(), ""))
                .cookieManager(cookieManager)
                .delete();

        verifyToken("");
        verifyListener();
    }

    @Test
    @Order(7)
    void invalidateByUsername() throws URISyntaxException {
        final String username = "1";
        final CookieManager cookieManager = new CookieManager();
        final String port = getRandomPort();
        final String uri = toUrl(port, "");
        final String token = URLRequest
                .build(uri)
                .cookieManager(cookieManager)
                .param("username", username)
                .post()
                .getBody();
        assertNotEquals("", token);

        String sessionId = getSessionId(cookieManager, uri);
        assertNotNull(sessionId);

        Map<String, CookieManager> cookieManagers = new HashMap<>();
        for (String p : ports) {
            CookieManager cm = new CookieManager();
            String t = URLRequest.build(toUrl(p, ""))
                    .cookieManager(cm)
                    .param("username", username)
                    .post()
                    .getBody();
            assertNotEquals("", t);
            assertNotEquals(token, t);
            cookieManagers.put(p, cm);
        }

        syncSessionService.invalidateByUsername(username, sessionId);
        for (String p : ports) {
            syncSessionService.keepalive(sessionId);
            String t = retryGetToken(cookieManagers.get(p), toUrl(p, "/token"), "", 0);
            assertEquals("", t);
        }

        assertEquals(token, URLRequest
                .build(toUrl(port, "/token"))
                .cookieManager(cookieManager)
                .get()
                .getBody());
    }


    @Test
    @Order(8)
    void keepalive() throws InterruptedException, URISyntaxException {
        String token = createSession();
        assertNotEquals("", token);
        String sessionId = getSessionId(cookieManager, toUrl(getRandomPort(), ""));
        assertNotNull(sessionId);
        long timeout = syncSessionProperties.getTimeout().toMillis() * 2;
        for (int i = 0; i < timeout; i += 1000) {
            Thread.sleep(1000);
            assertTrue(syncSessionService.keepalive(sessionId));
        }
        verifyToken(token);
        syncSessionService.invalidate(sessionId);
        assertFalse(syncSessionService.keepalive(sessionId));
    }


    private String toUrl(String port, String path) {
        return "http://localhost:" + port + "/session" + path;
    }

    private String getRandomPort() {
        return ports.get(new Random().nextInt(ports.size()));
    }

    void verifyToken(String token) {
        for (String port : ports) {
            String t = retryGetToken(cookieManager, toUrl(port, "/token"), token, 0);
            assertEquals(token, t);
        }
    }

    String retryGetToken(CookieManager cookieManager, String url, String expected, int count) {
        String t = URLRequest.build(url)
                .cookieManager(cookieManager)
                .get()
                .getBody();
        if (Objects.equals(expected, t)) return t;
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return count < 10 ? retryGetToken(cookieManager, url, expected, count + 1) : t;
    }

    void verifyListener() throws InterruptedException {
        Thread.sleep(50);
        assertEquals(ports.size() + 1, jdbcTemplate.queryForObject("select count(*) from log", Long.class));
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from log where type = ?", Long.class, InvalidateBy.SELF));
    }

    String result(String port, String path, String method) {
        return result(port, path, method, null);
    }

    String result(String port, String path, String method, Map<String, Object> params) {
        return URLRequest
                .build(toUrl(port, path))
                .params(params)
                .cookieManager(cookieManager)
                .method(method)
                .go()
                .getBody();
    }

    String createSession() {
        Map<String, Object> params = new HashMap<>();
        params.put("username", "test");
        return result(getRandomPort(), "", URLRequestMethod.POST, params);
    }

    String getToken() {
        return result(getRandomPort(), "/token", URLRequestMethod.GET);
    }

    String getSessionId(CookieManager cookieManager, String uri) throws URISyntaxException {
        final List<HttpCookie> cookies = cookieManager.getCookieStore().get(new URI(uri));
        final String cookieName = syncSessionProperties.getCookieName();
        String sessionId = null;
        for (HttpCookie cookie : cookies) {
            if (cookieName.equalsIgnoreCase(cookie.getName())) {
                sessionId = cookie.getValue();
                break;
            }
        }
        return sessionId;
    }
}
