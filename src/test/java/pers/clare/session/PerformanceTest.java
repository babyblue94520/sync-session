package pers.clare.session;

import pers.clare.h2.H2Application;
import pers.clare.test.ApplicationTest2;
import pers.clare.test.config.SessionConfig;
import pers.clare.test.session.SyncSessionEventServiceImpl;
import pers.clare.urlrequest.URLRequest;
import pers.clare.util.PerformanceUtil;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.util.StringUtils;

import java.net.CookieManager;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import({SessionConfig.class})
@SpringBootTest(args = {"--h2.port=10090"})
class PerformanceTest {
    private static final List<String> ports = new ArrayList<>();

    private static final List<ApplicationContext> applications = new ArrayList<>();

    static {
        SyncSessionEventServiceImpl.reset();
        applications.add(
                SpringApplication.run(H2Application.class
                        , "--spring.profiles.active=h2server"
                        , "--h2.port=10090"
                )
        );
        for (int i = 0; i < 3; i++) {
            ports.add(String.valueOf(10000 + i));
        }
    }

    private String toUrl(String port, String path) {
        return "http://localhost:" + port + "/session" + path;
    }

    private String getRandomPort() {
        return ports.get(new Random().nextInt(ports.size()));
    }


    @BeforeAll
    void before() {
        for (String port : ports) {
            applications.add(SpringApplication.run(ApplicationTest2.class
                    , "--server.port=" + port
                    , "--sync-session.timeout=30m"
                    , "--h2.port=10090"
            ));
        }
    }

    @AfterAll
    void after() {
        for (ApplicationContext application : applications) {
            SpringApplication.exit(application);
        }
    }

    @Test
    @Order(99)
    void performance() throws Exception {
        System.setProperty("http.maxConnections", "500");
        Map<Long, CookieManager> cookieManagerMap = new ConcurrentHashMap<>();
        Map<CookieManager, String> tokenMap = new ConcurrentHashMap<>();
        AtomicLong failCount = new AtomicLong();

        PerformanceUtil.byCount(1000, (index) -> {
            CookieManager cm = new CookieManager();
            String token = URLRequest.build(toUrl(getRandomPort(), ""))
                    .cookieManager(cm)
                    .post()
                    .getBody();
            cookieManagerMap.put(index, cm);
            tokenMap.put(cm, token);

            if (!StringUtils.hasLength(token)) {
                failCount.incrementAndGet();
            }
        });

        int size = cookieManagerMap.size();
        PerformanceUtil.byCount(10000, (index) -> {
            long key = index % size + 1;
            CookieManager cm = cookieManagerMap.get(key);
            String token = URLRequest.build(toUrl(getRandomPort(), "/token"))
                    .cookieManager(cm)
                    .get()
                    .getBody();
            if (!Objects.equals(token, tokenMap.get(cm))) {
                failCount.incrementAndGet();
            }
        });

        assertEquals(0, failCount.get());
    }
}
