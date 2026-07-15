package pers.clare.session;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.ConfigurableApplicationContext;
import pers.clare.session.configuration.SyncSessionProperties;
import pers.clare.test.ApplicationTest;
import pers.clare.test.session.SyncSessionEventServiceImpl;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
@EnabledIfSystemProperty(named = SessionCreateLoadTest.ENABLED_PROPERTY, matches = "true")
class SessionCreateLoadTest {

    static final String ENABLED_PROPERTY = "test.load.enabled";
    private static final String RPS_PROPERTY = "test.load.rps";
    private static final String KEEP_ALIVE_RPS_PROPERTY = "test.load.keep-alive.rps";
    private static final String KEEP_ALIVE_PERCENT_PROPERTY = "test.load.keep-alive.percent";
    private static final String TIMEOUT_PROPERTY = "test.load.timeout";
    private static final String DURATION_PROPERTY = "test.load.duration";
    private static final String THREADS_PROPERTY = "test.load.threads";

    private static final int DEFAULT_RPS = 1000;
    private static final String DEFAULT_TIMEOUT = "1m";
    private static final String DEFAULT_DURATION = "10m";
    private static final int NODE_COUNT = 3;

    private final List<ConfigurableApplicationContext> contexts = new ArrayList<>();
    private final List<ConfigurableApplicationContext> appContexts = new ArrayList<>();
    private final List<Integer> appPorts = new CopyOnWriteArrayList<>();
    private final AtomicLong portCursor = new AtomicLong();
    private final AtomicLong keepAliveCursor = new AtomicLong();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeAll
    void beforeAll() {
        SyncSessionEventServiceImpl.reset();

        String profileName = DatabaseProfileSupport.selectedProfile();
        String timeout = System.getProperty(TIMEOUT_PROPERTY, DEFAULT_TIMEOUT);
        String dbName = getClass().getSimpleName().toLowerCase();
        Integer h2Port = null;

        if ("h2".equals(profileName)) {
            h2Port = TestPorts.freePort();
            contexts.add(TestH2Support.start(h2Port, "MYSQL", dbName));
        }

        for (int index = 0; index < NODE_COUNT; index++) {
            List<String> args = new ArrayList<>();
            args.add("--server.port=0");
            args.add("--spring.profiles.active=" + profileName);
            args.add("--timeout=" + timeout);
            args.add("--clazz=pers.clare.test.session.TokenSession");

            if (h2Port != null) {
                args.add("--spring.datasource.url=jdbc:h2:tcp://localhost:" + h2Port + "/mem:" + dbName + ";MODE=MYSQL;DATABASE_TO_UPPER=FALSE");
            }

            ConfigurableApplicationContext context = SpringApplication.run(ApplicationTest.class, args.toArray(String[]::new));
            contexts.add(context);
            appContexts.add(context);
            appPorts.add(Integer.parseInt(context.getEnvironment().getProperty("local.server.port")));
        }
    }

    @AfterAll
    void afterAll() {
        for (ConfigurableApplicationContext context : contexts) {
            context.close();
        }
        contexts.clear();
        appContexts.clear();
        appPorts.clear();
        SyncSessionEventServiceImpl.shutdown();
    }

    @Test
    void createSessionsAtConfiguredRate() throws Exception {
        int targetCreateRps = intProperty(RPS_PROPERTY, DEFAULT_RPS);
        int targetKeepAliveRps = intProperty(KEEP_ALIVE_RPS_PROPERTY, targetCreateRps);
        int keepAlivePercent = intProperty(KEEP_ALIVE_PERCENT_PROPERTY, 50);
        Duration duration = durationProperty(DURATION_PROPERTY, DEFAULT_DURATION);
        Duration timeout = durationProperty(TIMEOUT_PROPERTY, DEFAULT_TIMEOUT);
        int workerThreads = intProperty(THREADS_PROPERTY, defaultThreads(targetCreateRps + targetKeepAliveRps));

        Assertions.assertTrue(targetCreateRps > 0, () -> RPS_PROPERTY + " must be > 0");
        Assertions.assertTrue(targetKeepAliveRps >= 0, () -> KEEP_ALIVE_RPS_PROPERTY + " must be >= 0");
        Assertions.assertTrue(keepAlivePercent >= 0 && keepAlivePercent <= 100, () -> KEEP_ALIVE_PERCENT_PROPERTY + " must be between 0 and 100");
        Assertions.assertFalse(appPorts.isEmpty(), "No application nodes started");

        System.setProperty("http.maxConnections", String.valueOf(Math.max(200, workerThreads * 4)));

        ExecutorService executor = Executors.newFixedThreadPool(workerThreads);
        ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor();
        List<Future<Void>> futures = new ArrayList<>();
        OperationStats createStats = new OperationStats("create");
        OperationStats keepAliveStats = new OperationStats("keep-alive");
        List<ClientSession> keepAliveClients = new CopyOnWriteArrayList<>();
        long startTime = System.nanoTime();

        reporter.scheduleAtFixedRate(
                () -> printProgress(createStats, keepAliveStats, targetCreateRps, targetKeepAliveRps, timeout, workerThreads, keepAliveClients.size(), startTime),
                5,
                5,
                TimeUnit.SECONDS
        );

        long createIntervalNanos = Math.max(1L, TimeUnit.SECONDS.toNanos(1) / targetCreateRps);
        long keepAliveIntervalNanos = targetKeepAliveRps == 0 ? Long.MAX_VALUE : Math.max(1L, TimeUnit.SECONDS.toNanos(1) / targetKeepAliveRps);
        long deadline = System.nanoTime() + duration.toNanos();
        long nextCreateTick = System.nanoTime();
        long nextKeepAliveTick = System.nanoTime();
        long createSequence = 0;
        long keepAliveSequence = 0;

        while (System.nanoTime() < deadline) {
            long now = System.nanoTime();
            if (now >= nextCreateTick) {
                long sequence = ++createSequence;
                createStats.scheduledCount.incrementAndGet();
                futures.add(executor.submit(() -> createSession(createStats, keepAliveClients, sequence, keepAlivePercent)));
                nextCreateTick += createIntervalNanos;
                continue;
            }
            if (targetKeepAliveRps > 0 && now >= nextKeepAliveTick) {
                long sequence = ++keepAliveSequence;
                keepAliveStats.scheduledCount.incrementAndGet();
                futures.add(executor.submit(() -> keepAliveSession(keepAliveStats, keepAliveClients, sequence)));
                nextKeepAliveTick += keepAliveIntervalNanos;
                continue;
            }

            long waitNanos = Math.min(nextCreateTick, nextKeepAliveTick) - now;
            if (waitNanos > 0) {
                LockSupport.parkNanos(waitNanos);
            }
        }

        for (Future<Void> future : futures) {
            future.get();
        }
        executor.shutdownNow();
        reporter.shutdownNow();

        printSummary(createStats, keepAliveStats, targetCreateRps, targetKeepAliveRps, duration, timeout, workerThreads, keepAliveClients.size(), startTime);
        Assertions.assertEquals(0, createStats.failureCount.get(), "Create load test failed with " + createStats.failureCount.get() + " errors");
        Assertions.assertEquals(0, keepAliveStats.failureCount.get(), "Keep-alive load test failed with " + keepAliveStats.failureCount.get() + " errors");
    }

    private String toUrl(int port) {
        return "http://localhost:" + port + "/session";
    }

    private String toUrl(int port, String path) {
        return toUrl(port) + path;
    }

    private Void createSession(OperationStats stats, List<ClientSession> keepAliveClients, long sequence, int keepAlivePercent) {
        long start = System.nanoTime();
        int port = nextPort();
        try {
            CreatedSession createdSession = postCreate(port, "load-user-" + sequence);
            if (createdSession.token == null || createdSession.token.isBlank()) {
                stats.failureCount.incrementAndGet();
                stats.errors.add("Empty token for create request " + sequence);
            } else if (createdSession.sessionId == null || createdSession.sessionId.isBlank()) {
                stats.failureCount.incrementAndGet();
                stats.errors.add("Empty session id for create request " + sequence);
            } else {
                stats.successCount.incrementAndGet();
                if (shouldKeepAlive(sequence, keepAlivePercent)) {
                    keepAliveClients.add(new ClientSession(port, createdSession.sessionId, createdSession.token));
                }
            }
        } catch (Exception e) {
            stats.failureCount.incrementAndGet();
            stats.errors.add(sequence + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
        } finally {
            stats.totalLatencyNanos.addAndGet(System.nanoTime() - start);
        }
        return null;
    }

    private boolean shouldKeepAlive(long sequence, int keepAlivePercent) {
        if (keepAlivePercent <= 0) {
            return false;
        }
        if (keepAlivePercent >= 100) {
            return true;
        }
        return Math.floorMod((int) sequence, 100) < keepAlivePercent;
    }

    private Void keepAliveSession(OperationStats stats, List<ClientSession> keepAliveClients, long sequence) {
        long start = System.nanoTime();
        try {
            ClientSession clientSession = nextKeepAliveClient(keepAliveClients);
            if (clientSession == null) {
                stats.skippedCount.incrementAndGet();
                return null;
            }
            if (keepAlive(clientSession)) {
                stats.successCount.incrementAndGet();
            } else {
                stats.failureCount.incrementAndGet();
                stats.errors.add("Keep-alive returned false for request " + sequence);
            }
        } catch (Exception e) {
            stats.failureCount.incrementAndGet();
            stats.errors.add(sequence + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
        } finally {
            stats.totalLatencyNanos.addAndGet(System.nanoTime() - start);
        }
        return null;
    }

    private CreatedSession postCreate(int port, String username) throws Exception {
        String body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&includeSessionId=true";
        HttpRequest request = HttpRequest.newBuilder(URI.create(toUrl(port)))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();
        int separator = responseBody == null ? -1 : responseBody.indexOf(':');
        if (separator < 1) {
            return new CreatedSession("", responseBody);
        }
        return new CreatedSession(responseBody.substring(0, separator), responseBody.substring(separator + 1));
    }

    private boolean keepAlive(ClientSession clientSession) throws Exception {
        String body = "id=" + URLEncoder.encode(clientSession.sessionId, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(toUrl(clientSession.port, "/keepalive")))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return Boolean.parseBoolean(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body());
    }

    private ClientSession nextKeepAliveClient(List<ClientSession> keepAliveClients) {
        int size = keepAliveClients.size();
        if (size == 0) {
            return null;
        }
        int index = Math.floorMod((int) keepAliveCursor.getAndIncrement(), size);
        return keepAliveClients.get(index);
    }

    private int nextPort() {
        int index = Math.floorMod((int) portCursor.getAndIncrement(), appPorts.size());
        return appPorts.get(index);
    }

    private int defaultThreads(int totalTargetRps) {
        return Math.max(8, Math.min(128, totalTargetRps));
    }

    private void printProgress(OperationStats createStats, OperationStats keepAliveStats, int targetCreateRps, int targetKeepAliveRps, Duration timeout, int workerThreads, int keepAliveClientCount, long startTime) {
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

        System.out.printf(
                "session load progress: targetCreateRps=%d, actualCreateRps=%.2f, targetKeepAliveRps=%d, actualKeepAliveRps=%.2f, elapsed=%dms, sessionTimeout=%s, threads=%d, alive=%d, stored=%d, trackedKeepAliveClients=%d, create[%s], keepAlive[%s]%n",
                targetCreateRps,
                actualRps(createStats, elapsedMillis),
                targetKeepAliveRps,
                actualRps(keepAliveStats, elapsedMillis),
                elapsedMillis,
                timeout,
                workerThreads,
                aliveSessionCount(),
                storedSessionCount(),
                keepAliveClientCount,
                createStats.snapshot(),
                keepAliveStats.snapshot()
        );
    }

    private void printSummary(OperationStats createStats, OperationStats keepAliveStats, int targetCreateRps, int targetKeepAliveRps, Duration duration, Duration timeout, int workerThreads, int keepAliveClientCount, long startTime) {
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

        System.out.printf(
                "session load test completed: targetCreateRps=%d, actualCreateRps=%.2f, targetKeepAliveRps=%d, actualKeepAliveRps=%.2f, duration=%s, elapsed=%dms, sessionTimeout=%s, threads=%d, alive=%d, stored=%d, trackedKeepAliveClients=%d, create[%s], keepAlive[%s]%n",
                targetCreateRps,
                actualRps(createStats, elapsedMillis),
                targetKeepAliveRps,
                actualRps(keepAliveStats, elapsedMillis),
                duration,
                elapsedMillis,
                timeout,
                workerThreads,
                aliveSessionCount(),
                storedSessionCount(),
                keepAliveClientCount,
                createStats.snapshot(),
                keepAliveStats.snapshot()
        );

        printErrors(createStats);
        printErrors(keepAliveStats);
    }

    private double actualRps(OperationStats stats, long elapsedMillis) {
        return elapsedMillis <= 0 ? 0 : (double) stats.totalCount() * 1000 / elapsedMillis;
    }

    private void printErrors(OperationStats stats) {
        if (stats.errors.isEmpty()) return;
        System.out.println(stats.name + " sample errors:");
        stats.errors.stream().limit(10).forEach(System.out::println);
    }

    private long aliveSessionCount() {
        return sessionCount(" where last_access_time + max_inactive_interval > ?", System.currentTimeMillis());
    }

    private long storedSessionCount() {
        return sessionCount("", null);
    }

    private long sessionCount(String condition, Long time) {
        if (appContexts.isEmpty()) {
            return 0;
        }

        ConfigurableApplicationContext context = appContexts.get(0);
        DataSource dataSource = context.getBean(DataSource.class);
        SyncSessionProperties properties = context.getBean(SyncSessionProperties.class);
        String sql = "select count(*) from " + properties.getDs().getTableName() + condition;

        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (time != null) {
                statement.setLong(1, time);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
                return 0;
            }
        } catch (Exception e) {
            return -1;
        }
    }

    private int intProperty(String propertyName, int defaultValue) {
        return Integer.parseInt(System.getProperty(propertyName, String.valueOf(defaultValue)));
    }

    private Duration durationProperty(String propertyName, String defaultValue) {
        return DurationStyle.detectAndParse(System.getProperty(propertyName, defaultValue));
    }

    private static final class OperationStats {
        private final String name;
        private final AtomicLong scheduledCount = new AtomicLong();
        private final AtomicLong successCount = new AtomicLong();
        private final AtomicLong failureCount = new AtomicLong();
        private final AtomicLong skippedCount = new AtomicLong();
        private final AtomicLong totalLatencyNanos = new AtomicLong();
        private final ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();

        private OperationStats(String name) {
            this.name = name;
        }

        private long totalCount() {
            return successCount.get() + failureCount.get() + skippedCount.get();
        }

        private long inFlightCount() {
            return scheduledCount.get() - totalCount();
        }

        private double avgLatencyMillis() {
            long latencySamples = successCount.get() + failureCount.get();
            return latencySamples == 0 ? 0 : totalLatencyNanos.get() / 1_000_000d / latencySamples;
        }

        private String snapshot() {
            return String.format(
                    "scheduled=%d, inFlight=%d, total=%d, success=%d, failure=%d, skipped=%d, avgLatencyMs=%.2f",
                    scheduledCount.get(),
                    inFlightCount(),
                    totalCount(),
                    successCount.get(),
                    failureCount.get(),
                    skippedCount.get(),
                    avgLatencyMillis()
            );
        }
    }

    private record CreatedSession(String sessionId, String token) {
    }

    private record ClientSession(int port, String sessionId, String token) {
    }
}
