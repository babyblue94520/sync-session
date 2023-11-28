package pers.clare.session;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import pers.clare.h2.H2Application;
import pers.clare.test.ApplicationTest;
import pers.clare.test.session.SyncSessionEventServiceImpl;
import pers.clare.test.session.TokenSession;
import pers.clare.util.PerformanceUtil;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@DisplayName("AvailableTest")
@TestInstance(PER_CLASS)
@SpringBootTest(classes = ApplicationTest.class, args = {"--sync-session.timeout=30m"})
class EventAvailableTest {

    @Autowired
    private SyncSessionEventServiceImpl eventService;

    @Autowired
    private SyncSessionService<TokenSession> sessionService;

    private static final ApplicationContext h2server;

    static {
        h2server = SpringApplication.run(H2Application.class
                , "--spring.profiles.active=h2server"
                , "--h2.port=9090"
        );
    }

    @AfterAll
    void after() {
        if (h2server != null) SpringApplication.exit(h2server);
    }

    @Test
    void available() {
        eventService.setAvailable(true);
        TokenSession session = sessionService.create(System.currentTimeMillis(), "test", "127.0.0.1");
        assertSame(session, sessionService.find(session.getId()));

        eventService.setAvailable(false);
        assertNotSame(session, sessionService.find(session.getId()));

        eventService.setAvailable(true);
        session = sessionService.find(session.getId());
        assertSame(session, sessionService.find(session.getId()));
    }

    @Test
    void performance() throws Exception {
        SyncSessionEventServiceImpl.reset();
        eventService.setAvailable(true);
        run();
        eventService.setAvailable(false);
        run();
        eventService.setAvailable(true);
        run();
        eventService.setAvailable(false);
        run();
        eventService.setAvailable(true);
    }

    void run() throws Exception {
        TokenSession session = sessionService.create(System.currentTimeMillis(), "test", "127.0.0.1");
        PerformanceUtil.byCount("", 10, 100000, (index) -> {
            assertNotNull(sessionService.find(session.getId()));
        });
    }
}
