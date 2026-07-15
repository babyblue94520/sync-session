package pers.clare.session.listener;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import pers.clare.session.SyncSessionRequestContext;

public class SessionAsyncListener implements AsyncListener {
    private SyncSessionRequestContext<?> sessionContext;

    public SessionAsyncListener(SyncSessionRequestContext<?> sessionContext) {
        this.sessionContext = sessionContext;
    }

    @Override
    public void onComplete(AsyncEvent asyncEvent) {
        if (sessionContext == null) return;
        sessionContext.refreshSession();
        sessionContext = null;
    }

    @Override
    public void onTimeout(AsyncEvent asyncEvent) {
    }

    @Override
    public void onError(AsyncEvent asyncEvent) {
    }

    @Override
    public void onStartAsync(AsyncEvent asyncEvent) {
    }
}
