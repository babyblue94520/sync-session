package pers.clare.session;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("unused")
public class RequestCacheHolder {

    RequestCacheHolder() {
    }

    private static final ThreadLocal<RequestCache<? extends SyncSession>> cache =
            new NamedThreadLocal<>("Request Cache Holder");

    @SuppressWarnings("unchecked")
    public static <T extends SyncSession> RequestCache<T> init(
            HttpServletRequest request
            , HttpServletResponse response
            , SyncSessionService<T> sessionService
    ) {
        RequestCache<T> req = (RequestCache<T>) cache.get();
        if (req == null) cache.set((req = new RequestCache<>()));
        req.init(request, response, sessionService);
        return req;
    }

    @SuppressWarnings("unchecked")
    public static RequestCache<SyncSession> get() {
        RequestCache<SyncSession> req = (RequestCache<SyncSession>) cache.get();
        if (req == null) throw new RuntimeException("RequestCacheHolder not init");
        return req;
    }

    @SuppressWarnings("unchecked")
    public static <T extends SyncSession> RequestCache<T> get(Class<T> clazz) {
        RequestCache<T> req = (RequestCache<T>) cache.get();
        if (req == null) throw new RuntimeException("RequestCacheHolder not init");
        return req;
    }

    static class NamedThreadLocal<T> extends ThreadLocal<T> {
        private final String name;

        public NamedThreadLocal(String name) {
            if (name == null) throw new IllegalArgumentException("Name must not be empty");
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

}


