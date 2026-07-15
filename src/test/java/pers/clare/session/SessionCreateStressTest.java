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
import pers.clare.session.service.SyncSessionService;
import pers.clare.test.ApplicationTest;
import pers.clare.test.session.SyncSessionEventServiceImpl;
import pers.clare.test.session.TokenSession;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
@EnabledIfSystemProperty(named = SessionCreateStressTest.ENABLED_PROPERTY, matches = "true")
abstract class SessionCreateStressTest {

    static final String ENABLED_PROPERTY = "test.stress.enabled";
    private static final String THREADS_PROPERTY = "test.stress.threads";
    private static final String SESSIONS_PROPERTY = "test.stress.sessions";
    private static final String TIMEOUT_PROPERTY = "test.stress.timeout";
    private static final String REPORT_INTERVAL_PROPERTY = "test.stress.report-interval";
    private static final int ERROR_SAMPLE_LIMIT = 20;

    private static final int DEFAULT_THREADS = 100;
    private static final int DEFAULT_SESSIONS = 1_000_000;
    private static final int CACHE_ACCESS_OPERATIONS = 100_000_000;
    private static final int MAX_LATENCY_SAMPLES = 1_000_000;
    private static final String DEFAULT_TIMEOUT = "30m";
    private static final String DEFAULT_REPORT_INTERVAL = "5s";

    private ConfigurableApplicationContext appContext;
    private SyncSessionService<TokenSession> syncSessionService;
    private SyncSessionEventServiceImpl sessionEventService;
    private DataSource dataSource;
    private String tableName;

    @BeforeAll
    void beforeAll() {
        SyncSessionEventServiceImpl.reset();

        String profileName = profileName();
        String timeout = System.getProperty(TIMEOUT_PROPERTY, DEFAULT_TIMEOUT);
        String dbName = getClass().getSimpleName().toLowerCase(Locale.ROOT);

        var args = new java.util.ArrayList<String>();
        args.add("--server.port=0");
        args.add("--spring.profiles.active=" + profileName);
        args.add("--sync-session.timeout=" + timeout);
        args.add("--clazz=pers.clare.test.session.TokenSession");
        configureDatabase(args, dbName);

        appContext = SpringApplication.run(ApplicationTest.class, args.toArray(String[]::new));
        syncSessionService = (SyncSessionService<TokenSession>) appContext.getBean(SyncSessionService.class);
        sessionEventService = appContext.getBean(SyncSessionEventServiceImpl.class);
        dataSource = appContext.getBean(DataSource.class);
        tableName = appContext.getBean(SyncSessionProperties.class).getDs().getTableName();
    }

    @AfterAll
    void afterAll() {
        if (appContext != null) {
            appContext.close();
        }
        closeDatabase();
        SyncSessionEventServiceImpl.shutdown();
    }

    @Test
    void createSessionsUnderPressure() throws Exception {
        int workerThreads = intProperty(THREADS_PROPERTY, DEFAULT_THREADS);
        int targetSessions = intProperty(SESSIONS_PROPERTY, DEFAULT_SESSIONS);
        Duration reportInterval = durationProperty(REPORT_INTERVAL_PROPERTY, DEFAULT_REPORT_INTERVAL);
        long reportIntervalSeconds = Math.max(1L, reportInterval.toSeconds());

        Assertions.assertTrue(workerThreads > 0, () -> THREADS_PROPERTY + " must be > 0");
        Assertions.assertTrue(targetSessions > 0, () -> SESSIONS_PROPERTY + " must be > 0");

        System.setProperty("http.maxConnections", String.valueOf(Math.max(200, workerThreads * 4)));

        forceGc();
        long heapBefore = usedHeapBytes();
        long tableRowsBefore = currentTableRowCount();

        long[][] latenciesNanos = {new long[targetSessions]};
        String[][] sessionIds = {new String[targetSessions]};
        AtomicInteger nextSequence = new AtomicInteger();
        AtomicLong successCount = new AtomicLong();
        AtomicLong failureCount = new AtomicLong();
        ConcurrentLinkedQueue<String> errorSamples = new ConcurrentLinkedQueue<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workerThreads);
        ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor();
        List<Future<?>> futures = new ArrayList<>(workerThreads);

        long startTime = System.nanoTime();
        reporter.scheduleAtFixedRate(
                () -> printProgress("create", targetSessions, successCount.get(), failureCount.get(), startTime),
                reportIntervalSeconds,
                reportIntervalSeconds,
                TimeUnit.SECONDS
        );

        for (int workerIndex = 0; workerIndex < workerThreads; workerIndex++) {
            futures.add(executor.submit(() -> runCreateWorker(targetSessions, nextSequence, latenciesNanos[0], sessionIds[0], successCount, failureCount, errorSamples, startLatch)));
        }

        startLatch.countDown();

        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();
        reporter.shutdownNow();

        long elapsedNanos = System.nanoTime() - startTime;
        BenchmarkResult createResult = BenchmarkResult.of("create", elapsedNanos, successCount.get(), failureCount.get(), latenciesNanos[0]);
        printErrorSamples(errorSamples);
        latenciesNanos[0] = null;
        futures.clear();
        errorSamples.clear();

        forceGc();
        BenchmarkResult cacheHitResult = runAccessBenchmark("cacheHit", sessionIds[0], CACHE_ACCESS_OPERATIONS, workerThreads, reportIntervalSeconds, true);
        forceGc();
        BenchmarkResult databaseFindResult = runAccessBenchmark("databaseFind", sessionIds[0], targetSessions, workerThreads, reportIntervalSeconds, false);
        sessionEventService.setAvailable(true);
        sessionIds[0] = null;

        forceGc();
        long heapAfter = usedHeapBytes();
        int localSessionCountAfter = currentLocalSessionCount();

        long heapUsedBySessions = Math.max(0L, heapAfter - heapBefore);
        long tableRowsAfter = currentTableRowCount();
        long tableRowsAdded = tableRowsAfter - tableRowsBefore;

        System.out.printf(
                Locale.ROOT,
                "session stress completed: threads=%d, targetSessions=%d, create[%s], cacheHit[%s], databaseFind[%s], jvmSessions[count=%d, heapDelta=%s], tableRows[count=%d, added=%d]%n",
                workerThreads,
                targetSessions,
                createResult.format(),
                cacheHitResult.format(),
                databaseFindResult.format(),
                localSessionCountAfter,
                formatBytes(heapUsedBySessions),
                tableRowsAfter,
                tableRowsAdded
        );

        Assertions.assertEquals(0, failureCount.get(), "Stress test failed with " + failureCount.get() + " errors");
        Assertions.assertEquals(targetSessions, successCount.get(), "Did not create expected session count");
        Assertions.assertEquals(CACHE_ACCESS_OPERATIONS, cacheHitResult.success(), "Did not complete expected cache hit access count");
        Assertions.assertEquals(0, cacheHitResult.failure(), "Cache hit access failed with " + cacheHitResult.failure() + " errors");
        Assertions.assertEquals(targetSessions, databaseFindResult.success(), "Did not complete expected database access count");
        Assertions.assertEquals(0, databaseFindResult.failure(), "Database access failed with " + databaseFindResult.failure() + " errors");
        Assertions.assertEquals(targetSessions, tableRowsAdded, "Did not insert expected session table row count");
    }

    private void runCreateWorker(
            int targetSessions,
            AtomicInteger nextSequence,
            long[] latenciesNanos,
            String[] sessionIds,
            AtomicLong successCount,
            AtomicLong failureCount,
            Queue<String> errorSamples,
            CountDownLatch startLatch
    ) {
        try {
            startLatch.await();
            while (true) {
                int sequence = nextSequence.getAndIncrement();
                if (sequence >= targetSessions) {
                    return;
                }

                long start = System.nanoTime();
                try {
                    TokenSession session = createSession(sequence);
                    if (session == null || session.getId() == null || session.getId().isBlank()) {
                        failureCount.incrementAndGet();
                        addErrorSample(errorSamples, "sequence=" + sequence + ", empty session");
                    } else {
                        sessionIds[sequence] = session.getId();
                        latenciesNanos[sequence] = System.nanoTime() - start;
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    addErrorSample(errorSamples, "sequence=" + sequence + ", " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private TokenSession createSession(int sequence) {
        long now = System.currentTimeMillis();
        TokenSession session = syncSessionService.create(now, "stress-test", "127.0.0.1");
        session.setUsername("stress-user-" + sequence);
        session.setCsrfToken(UUID.randomUUID().toString());
        syncSessionService.refresh(session);
        return session;
    }

    private BenchmarkResult runAccessBenchmark(
            String name,
            String[] sessionIds,
            int targetAccesses,
            int workerThreads,
            long reportIntervalSeconds,
            boolean eventAvailable
    ) throws Exception {
        sessionEventService.setAvailable(eventAvailable);
        long[] latenciesNanos = new long[Math.min(targetAccesses, MAX_LATENCY_SAMPLES)];
        AtomicInteger nextSequence = new AtomicInteger();
        AtomicLong successCount = new AtomicLong();
        AtomicLong failureCount = new AtomicLong();
        ConcurrentLinkedQueue<String> errorSamples = new ConcurrentLinkedQueue<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workerThreads);
        ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor();
        List<Future<?>> futures = new ArrayList<>(workerThreads);

        long startTime = System.nanoTime();
        reporter.scheduleAtFixedRate(
                () -> printProgress(name, targetAccesses, successCount.get(), failureCount.get(), startTime),
                reportIntervalSeconds,
                reportIntervalSeconds,
                TimeUnit.SECONDS
        );

        for (int workerIndex = 0; workerIndex < workerThreads; workerIndex++) {
            futures.add(executor.submit(() -> runAccessWorker(name, sessionIds, targetAccesses, nextSequence, latenciesNanos, successCount, failureCount, errorSamples, startLatch)));
        }

        startLatch.countDown();

        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();
        reporter.shutdownNow();

        long elapsedNanos = System.nanoTime() - startTime;
        BenchmarkResult result = BenchmarkResult.of(name, elapsedNanos, successCount.get(), failureCount.get(), latenciesNanos);
        printErrorSamples(errorSamples);
        futures.clear();
        errorSamples.clear();
        return result;
    }

    private void runAccessWorker(
            String name,
            String[] sessionIds,
            int targetAccesses,
            AtomicInteger nextSequence,
            long[] latenciesNanos,
            AtomicLong successCount,
            AtomicLong failureCount,
            Queue<String> errorSamples,
            CountDownLatch startLatch
    ) {
        try {
            startLatch.await();
            while (true) {
                int sequence = nextSequence.getAndIncrement();
                if (sequence >= targetAccesses) {
                    return;
                }

                String sessionId = sessionIds[sequence % sessionIds.length];
                long start = System.nanoTime();
                try {
                    TokenSession session = syncSessionService.find(sessionId);
                    if (session == null || session.getId() == null || session.getId().isBlank()) {
                        failureCount.incrementAndGet();
                        addErrorSample(errorSamples, name + ", sequence=" + sequence + ", empty session");
                    } else {
                        if (sequence < latenciesNanos.length) {
                            latenciesNanos[sequence] = System.nanoTime() - start;
                        }
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    addErrorSample(errorSamples, name + ", sequence=" + sequence + ", " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void printProgress(String name, int targetOperations, long successCount, long failureCount, long startTime) {
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        double operationRps = elapsedMillis <= 0 ? 0 : successCount * 1000d / elapsedMillis;
        System.out.printf(
                Locale.ROOT,
                "session stress progress: phase=%s, targetOperations=%d, success=%d, failure=%d, elapsed=%dms, rps=%.2f, jvmSessions=%d%n",
                name,
                targetOperations,
                successCount,
                failureCount,
                elapsedMillis,
                operationRps,
                currentLocalSessionCount()
        );
    }

    private int currentLocalSessionCount() {
        try {
            Field field = findField(syncSessionService.getClass(), "sessions");
            if (field == null) {
                return -1;
            }
            field.setAccessible(true);
            Map<?, ?> sessions = (Map<?, ?>) field.get(syncSessionService);
            return sessions.size();
        } catch (Exception e) {
            return -1;
        }
    }

    private long currentTableRowCount() throws Exception {
        try (var connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select count(*) from " + tableName)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private void addErrorSample(Queue<String> errorSamples, String error) {
        if (errorSamples.size() >= ERROR_SAMPLE_LIMIT) {
            return;
        }
        errorSamples.add(error);
    }

    private void printErrorSamples(Queue<String> errorSamples) {
        if (errorSamples.isEmpty()) {
            return;
        }
        System.out.println("session stress sample errors:");
        errorSamples.forEach(System.out::println);
    }

    private long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private String formatBytes(long bytes) {
        return formatBytes((double) bytes);
    }

    private String formatBytes(double bytes) {
        double mb = bytes / 1024d / 1024d;
        double gb = mb / 1024d;
        return String.format(Locale.ROOT, "%.2fMB (%.4fGB)", mb, gb);
    }

    private void forceGc() throws InterruptedException {
        System.gc();
        Thread.sleep(1000);
        System.runFinalization();
        Thread.sleep(500);
    }

    private int intProperty(String propertyName, int defaultValue) {
        return Integer.parseInt(System.getProperty(propertyName, String.valueOf(defaultValue)));
    }

    private Duration durationProperty(String propertyName, String defaultValue) {
        return DurationStyle.detectAndParse(System.getProperty(propertyName, defaultValue));
    }

    protected abstract String profileName();

    protected void configureDatabase(List<String> args, String dbName) {
    }

    protected void closeDatabase() {
    }

    private record LatencySummary(long minNanos, long midNanos, long maxNanos) {
        static LatencySummary of(long[] latenciesNanos, int size) {
            if (size <= 0) {
                return new LatencySummary(0, 0, 0);
            }
            long[] copy = Arrays.copyOf(latenciesNanos, size);
            Arrays.sort(copy);
            int trim = (int) Math.floor(size * 0.1d);
            int fromIndex = Math.min(trim, size - 1);
            int toIndex = Math.max(fromIndex, size - trim - 1);
            int midIndex = fromIndex + ((toIndex - fromIndex) / 2);
            return new LatencySummary(copy[0], copy[midIndex], copy[size - 1]);
        }

        double minMs() {
            return minNanos / 1_000_000d;
        }

        double midMs() {
            return midNanos / 1_000_000d;
        }

        double maxMs() {
            return maxNanos / 1_000_000d;
        }
    }

    private record BenchmarkResult(String name, long elapsedNanos, long success, long failure, LatencySummary latencySummary) {
        static BenchmarkResult of(String name, long elapsedNanos, long success, long failure, long[] latenciesNanos) {
            return new BenchmarkResult(name, elapsedNanos, success, failure, LatencySummary.of(latenciesNanos, Math.min(Math.toIntExact(success), latenciesNanos.length)));
        }

        String format() {
            double rps = elapsedNanos <= 0 ? 0 : success * 1_000_000_000d / elapsedNanos;
            return String.format(
                    Locale.ROOT,
                    "success=%d, failure=%d, elapsed=%dms, rps=%.2f, latencyMs[min=%.3f, mid=%.3f, max=%.3f]",
                    success,
                    failure,
                    TimeUnit.NANOSECONDS.toMillis(elapsedNanos),
                    rps,
                    latencySummary.minMs(),
                    latencySummary.midMs(),
                    latencySummary.maxMs()
            );
        }
    }
}
