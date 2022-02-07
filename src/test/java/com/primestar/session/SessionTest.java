package com.primestar.session;

import com.primestar.initschema.EnableInitSchema;
import com.primestar.session.constant.InvalidateBy;
import com.primestar.test.config.SessionListenerConfig;
import com.primestar.urlrequest.URLRequest;
import com.primestar.urlrequest.URLRequestMethod;
import com.primestar.test.ApplicationTest2;
import com.primestar.test.config.SessionConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@EnableInitSchema
@Sql(scripts = {"/schema/schema.sql"})
@DisplayName("SessionTest")
@TestInstance(PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import({SessionConfig.class, SessionListenerConfig.class})
@SpringBootTest(classes = ApplicationTest.class)
class SessionTest {
    private static final String[] ports = {
            "10001"
            , "10002"
            , "10003"
    };

    static {
        System.setProperty("test.database", UUID.randomUUID().toString());
    }

    private final CookieManager cookieManager = new CookieManager();

    @Autowired
    private SyncSessionProperties syncSessionProperties;

    @Autowired
    private SyncSessionService<?> syncSessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String toUrl(String port, String path) {
        return "http://localhost:" + port + "/session" + path;
    }

    private String getRandomPort() {
        return ports[new Random().nextInt(ports.length)];
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
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return count < 10 ? retryGetToken(cookieManager, url, expected, count + 1) : t;
    }

    void verifyListener() throws InterruptedException {
        Thread.sleep(50);
        assertEquals(ports.length + 1, jdbcTemplate.queryForObject("select count(*) from log", Long.class));
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from log where type = ?", Long.class, InvalidateBy.SELF));

    }

    String result(String port, String path, String method) {
        return URLRequest
                .build(toUrl(port, path))
                .cookieManager(cookieManager)
                .method(method)
                .go()
                .getBody();
    }

    String createSession() {
        return result(getRandomPort(), "", URLRequestMethod.POST);
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

    private static final List<ApplicationContext> applications = new ArrayList<>();

    @BeforeAll
    void before() {
        for (String port : ports) {
            applications.add(SpringApplication.run(ApplicationTest2.class, "--server.port=" + port));
        }
    }

    @BeforeEach
    void beforeEach() {
        jdbcTemplate.execute("truncate log");
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
        Thread.sleep(50);
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
            assertEquals(token, URLRequest.build(toUrl(ports[0], "/token"))
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
            URLRequest.build(toUrl(ports[0], "/ping"))
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

    @Test
    @Order(99)
    void performance() throws InterruptedException {
        Map<CookieManager, CookieManager> cookieManagers = new ConcurrentHashMap<>();
        int thread = 5;
        int count = 100;
        int round = 1000;

        long t = System.currentTimeMillis();
        multi(thread, () -> {
            for (int j = 0; j < count; j++) {
                CookieManager cm = new CookieManager();
                URLRequest.build(toUrl(getRandomPort(), ""))
                        .cookieManager(cm)
                        .post();
                cookieManagers.put(cm, cm);
            }
            return null;
        });
        System.out.printf("\n%d rps", (thread * count * 1000) / (System.currentTimeMillis() - t));

        t = System.currentTimeMillis();
        multi(thread, () -> {
            for (CookieManager cm : cookieManagers.values()) {
                URLRequest.build(toUrl(getRandomPort(), "/token"))
                        .cookieManager(cm)
                        .get()
                        .getBody()
                ;
            }
            return null;
        });
        System.out.printf("\n%d rps", (thread * count * round * 1000) / (System.currentTimeMillis() - t));
    }

    static void multi(int thread, Callable<Void> callable) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(thread);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < thread; i++) {
            tasks.add(callable);
        }
        executorService.invokeAll(tasks).forEach(f -> {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        });
        executorService.shutdown();
    }

}
