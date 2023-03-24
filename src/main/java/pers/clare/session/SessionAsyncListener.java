package pers.clare.session;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;

public class SessionAsyncListener implements AsyncListener {
    private RequestCache<?> requestCache;

    public SessionAsyncListener(RequestCache<?> requestCache) {
        this.requestCache = requestCache;
    }

    @Override
    public void onComplete(AsyncEvent asyncEvent) {
        if (requestCache == null) return;
        requestCache.refreshSession();
        requestCache = null;
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
