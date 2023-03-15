package pers.clare.test.session;

import pers.clare.session.SyncSessionEventService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;


public class SyncSessionEventServiceImpl implements SyncSessionEventService {
    private static final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    private static final ExecutorService executor = Executors.newFixedThreadPool(1);

    private boolean available = true;

    public static void reset() {
        listeners.clear();
    }

    @Override
    public void send( String body) {
        executor.submit(() -> listeners.forEach(consumer -> consumer.accept(body)));
    }

    @Override
    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

}
