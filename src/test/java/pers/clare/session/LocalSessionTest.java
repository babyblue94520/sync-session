package pers.clare.session;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import pers.clare.test2.ApplicationTest2;
import pers.clare.urlrequest.URLRequest;
import pers.clare.urlrequest.URLRequestMethod;
import pers.clare.urlrequest.URLResponse;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@Log4j2
@DisplayName("LocalSessionTest")
@TestInstance(PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LocalSessionTest {

    private static final Path LOCAL_STORE_FILE = Path.of("test", "aa", "sessions");
    private final CookieManager cookieManager = new CookieManager();

    private final String port = "8080";

    ConfigurableApplicationContext start(boolean persistence) {
        return start(persistence, null);
    }

    ConfigurableApplicationContext start(boolean persistence, Duration timeout) {
        List<String> args = new ArrayList<>();
        args.add("--server.port=" + port);
        args.add("--spring.profiles.active=local");
        args.add("--sync-session.local.persistence=" + persistence);
        if (timeout != null) {
            args.add("--sync-session.timeout=" + timeout.toMillis() + "ms");
        }
        return SpringApplication.run(ApplicationTest2.class, args.toArray(String[]::new));
    }

    @BeforeEach
    void beforeEach() throws Exception {
        Files.deleteIfExists(LOCAL_STORE_FILE);
    }

    @Order(1)
    @Test
    void test() throws Exception {
        ConfigurableApplicationContext context = start(false);
        String session = createSession();
        assertEquals(session, getToken());
        context.close();
        context = start(false);
        assertEquals("", getToken());
        context.close();
    }

    @Order(2)
    @Test
    void persistence() {
        ConfigurableApplicationContext context = start(true);
        String session = createSession();
        assertEquals(session, getToken());
        context.close();
        context = start(true);
        assertEquals(session, getToken());
        context.close();
    }

    @Order(3)
    @Test
    void expiredSessionIsNotPersisted() throws Exception {
        ConfigurableApplicationContext context = start(true, Duration.ofSeconds(1));
        String session = createSession();
        assertEquals(session, getToken());
        Thread.sleep(1200);
        String expiredToken = getToken();
        Assertions.assertTrue(expiredToken == null || expiredToken.isEmpty());
        context.close();
        Assertions.assertFalse(Files.exists(LOCAL_STORE_FILE));
    }


    private String toUrl(String port, String path) {
        return "http://localhost:" + port + "/session" + path;
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
        params.put("includeSessionId", true);
        URLResponse<String> response = URLRequest
                .build(toUrl(port, ""))
                .params(params)
                .cookieManager(cookieManager)
                .method(URLRequestMethod.POST)
                .go();
        String body = response.getBody();
        int separator = body == null ? -1 : body.indexOf(':');
        if (separator < 1) {
            return body;
        }
        ensureCookie(body.substring(0, separator));
        return body.substring(separator + 1);
    }

    String getToken() {
        return result(port, "/token", URLRequestMethod.GET);
    }

    private void ensureCookie(String sessionId) {
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
        cookieManager.getCookieStore().add(URI.create(toUrl(port, "")), cookie);
    }
}
