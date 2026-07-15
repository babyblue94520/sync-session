package pers.clare.test.session;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import pers.clare.session.event.SyncSessionEventService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

@Slf4j
@Setter
public class SyncSessionEventServiceImpl implements SyncSessionEventService {
    private static final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    private static volatile ExecutorService executor = newExecutor();

    private boolean available = true;

    public static void reset() {
        listeners.clear();
        executor.shutdownNow();
        executor = newExecutor();
    }

    public static void shutdown() {
        listeners.clear();
        executor.shutdownNow();
    }

    private static ExecutorService newExecutor() {
        return Executors.newFixedThreadPool(1, newDaemonThreadFactory());
    }

    private static ThreadFactory newDaemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "sync-session-test-event");
            thread.setDaemon(true);
            return thread;
        };
    }

    @Override
    public void send(String body) {
        Runnable task = () -> listeners.forEach(consumer -> {
            try {
                consumer.accept(body);
            } catch (Exception e) {
                log.error("Failed to dispatch sync-session test event.", e);
            }
        });
        try {
            executor.submit(task);
        } catch (RejectedExecutionException e) {
            log.warn("Sync-session test event executor rejected task; dispatching inline.", e);
            task.run();
        }
    }

    @Override
    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

}
