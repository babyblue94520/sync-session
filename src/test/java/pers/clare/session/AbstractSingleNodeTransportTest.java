package pers.clare.session;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import pers.clare.test.ApplicationTest;
import pers.clare.urlrequest.URLRequest;
import pers.clare.urlrequest.URLRequestMethod;
import pers.clare.urlrequest.URLResponse;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

abstract class AbstractSingleNodeTransportTest {

    private ConfigurableApplicationContext h2Context;
    private ConfigurableApplicationContext appContext;
    private int port;

    protected abstract String[] extraArgs();

    protected abstract void resetClientState();

    protected abstract URLRequest<String> applySession(URLRequest<String> request);

    protected abstract void captureSession(URLResponse<String> response);

    protected abstract void storeSessionId(String sessionId);

    @BeforeAll
    void beforeAll() {
        int h2Port = TestPorts.freePort();
        h2Context = TestH2Support.start(h2Port, "MYSQL", transportDbName());
        String[] args = new String[extraArgs().length + 5];
        args[0] = "--server.port=0";
        args[1] = "--spring.profiles.active=h2";
        args[2] = "--h2.port=" + h2Port;
        args[3] = "--h2.mode=MYSQL";
        args[4] = "--h2.dbName=" + transportDbName();
        System.arraycopy(extraArgs(), 0, args, 5, extraArgs().length);
        appContext = SpringApplication.run(ApplicationTest.class, args);
        port = Integer.parseInt(appContext.getEnvironment().getProperty("local.server.port"));
    }

    @BeforeEach
    void beforeEach() {
        resetClientState();
    }

    @AfterAll
    void afterAll() {
        if (appContext != null) {
            appContext.close();
        }
        if (h2Context != null) {
            h2Context.close();
        }
    }

    @Test
    void createAndReadToken() {
        String created = createSession();
        assertNotNull(created);
        assertNotEquals("", created);
        assertEquals(created, getToken());
    }

    @Test
    void resetTokenChangesValue() {
        String created = createSession();
        String reset = result("/token/reset", URLRequestMethod.POST, null);
        assertNotNull(reset);
        assertNotEquals("", reset);
        assertNotEquals(created, reset);
        assertEquals(reset, getToken());
    }

    @Test
    void invalidateClearsToken() {
        createSession();
        result("", URLRequestMethod.DELETE, null);
        String token = getToken();
        assertTrue(token == null || token.isEmpty());
    }

    protected String transportDbName() {
        return getClass().getSimpleName().toLowerCase();
    }

    private String createSession() {
        Map<String, Object> params = new HashMap<>();
        params.put("username", "transport-user");
        params.put("includeSessionId", true);
        String created = result("", URLRequestMethod.POST, params);
        int separator = created == null ? -1 : created.indexOf(':');
        if (separator < 1) {
            return created;
        }
        storeSessionId(created.substring(0, separator));
        return created.substring(separator + 1);
    }

    private String getToken() {
        return result("/token", URLRequestMethod.GET, null);
    }

    private String result(String path, String method, Map<String, Object> params) {
        URLResponse<String> response = applySession(URLRequest.build("http://localhost:" + port + "/session" + path))
                .params(params)
                .method(method)
                .go();
        captureSession(response);
        return response.getBody();
    }
}
