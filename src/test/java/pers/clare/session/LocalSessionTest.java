package pers.clare.session;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import pers.clare.test2.ApplicationTest2;
import pers.clare.urlrequest.URLRequest;
import pers.clare.urlrequest.URLRequestMethod;

import java.net.CookieManager;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@Log4j2
@DisplayName("LocalSessionTest")
@TestInstance(PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LocalSessionTest {

    private final CookieManager cookieManager = new CookieManager();

    private final String port = "8080";

    ConfigurableApplicationContext start(boolean persistence) throws Exception {
        return SpringApplication.run(ApplicationTest2.class
                , "--server.port=" + port
                , "--spring.profiles.active=local"
                , "--sync-session.local.persistence=" + persistence
        );
    }

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

    @Test
    void persistence() throws Exception {
        ConfigurableApplicationContext context = start(true);
        String session = createSession();
        assertEquals(session, getToken());
        context.close();
        context =  start(true);
        assertEquals(session, getToken());
        context.close();
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
        return result(port, "", URLRequestMethod.POST, params);
    }

    String getToken() {
        return result(port, "/token", URLRequestMethod.GET);
    }
}
