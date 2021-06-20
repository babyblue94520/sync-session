package pers.clare.session;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pers.clare.session.SyncSessionEventService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


public abstract class AbstractSyncSessionEventService implements SyncSessionEventService {
    private static final Logger log = LogManager.getLogger();

    private final List<Runnable> connectedListeners = new CopyOnWriteArrayList<>();

    @Override
    public Runnable onConnected(Runnable runnable) {
        connectedListeners.add(runnable);
        return runnable;
    }

    protected void publishConnectedEvent(){
        for (Runnable runnable : connectedListeners) {
            try {
                runnable.run();
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }
}
