package pers.clare.session.event;

import java.util.function.Consumer;

public interface SyncSessionEventService {

    /**
     * Send session clear events.
     */
    void send( String body);

    /**
     * Listen for session clear events.
     */
    void addListener( Consumer<String> listener);

    /*
     * Affects whether the session manage can be cached.
     */
    boolean isAvailable();
}
