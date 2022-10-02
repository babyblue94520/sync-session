package pers.clare.test.session;

import pers.clare.session.AbstractSyncSessionEventService;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;


public class SyncSessionEventServiceImpl extends AbstractSyncSessionEventService {

    private static Map<String, List<Consumer<String>>> topicListenerMap = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(1);

    public static void reset(){
        topicListenerMap = new ConcurrentHashMap<>();
    }

    @Override
    public Runnable onConnected(Runnable runnable) {
        return null;
    }

    @Override
    public String send(String topic, String body) {
        executor.submit(() -> {
            topicListenerMap.getOrDefault(topic, Collections.emptyList())
                    .forEach(consumer -> {
                        consumer.accept(body);
                    });
        });
        return body;
    }

    @Override
    public Consumer<String> addListener(String topic, Consumer<String> listener) {
        topicListenerMap.computeIfAbsent(topic, (key) -> new CopyOnWriteArrayList<>()).add(listener);
        return listener;
    }
}
