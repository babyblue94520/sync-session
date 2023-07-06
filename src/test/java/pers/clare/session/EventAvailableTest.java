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
import pers.clare.test.ApplicationTest2;
import pers.clare.test.session.SyncSessionEventServiceImpl;
import pers.clare.test.session.TokenSession;
import pers.clare.test.session.TokenSessionService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@DisplayName("AvailableTest")
@TestInstance(PER_CLASS)
@SpringBootTest(classes = ApplicationTest2.class, args = {"--sync-session.timeout=30m"})
class EventAvailableTest {

    @Autowired
    private SyncSessionEventServiceImpl eventService;

    @Autowired
    private TokenSessionService sessionService;

    private static ApplicationContext h2server;

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
    void performance() throws ExecutionException, InterruptedException {
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

    void run() throws ExecutionException, InterruptedException {
        int thread = 10;
        long max = 100000;
        AtomicLong count = new AtomicLong();

        TokenSession session = sessionService.create(System.currentTimeMillis(), "test", "127.0.0.1");
        long startTime = System.currentTimeMillis();
        Runnable shutdown = performance(thread, () -> {
            while (count.incrementAndGet() <= max) {
                assertNotNull(sessionService.find(session.getId()));
            }
            count.decrementAndGet();
            return null;
        });
        long time = System.currentTimeMillis() - startTime;
        System.out.printf("%d %d %d/s\n", max, time, count.get() * 1000 / (time == 0 ? 1 : time));
        shutdown.run();
    }


    private Runnable performance(int thread, Callable<Void> callable) throws InterruptedException, ExecutionException {
        ExecutorService executorService = Executors.newFixedThreadPool(thread);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < thread; i++) {
            tasks.add(callable);
        }
        for (Future<Void> future : executorService.invokeAll(tasks)) {
            future.get();
        }
        return executorService::shutdown;
    }
}
