package pers.clare.session;

import java.util.function.Consumer;

public interface SyncSessionEventService {

    Runnable onConnected(Runnable runnable);

    String send(String topic, String body);

    Consumer<String> addListener(String topic, Consumer<String> listener);
}
