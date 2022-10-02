package pers.clare.session;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("unused")
public class RequestCacheHolder {

    private static ThreadLocal<RequestCache<? extends SyncSession>> cache = new ThreadLocal<>();

    public static void setCache(ThreadLocal<RequestCache<? extends SyncSession>> cache){
        RequestCacheHolder.cache = cache;
    }
    public static void set(RequestCache<? extends SyncSession> requestCache) {
        cache.set(requestCache);
    }

    public static <T extends SyncSession> RequestCache<T> init(
            HttpServletRequest request
            , HttpServletResponse response
            , SyncSessionService<T> sessionService
    ) {
        RequestCache<T> req = new RequestCache<>();
        req.init(request, response, sessionService);
        cache.set(req);
        return req;
    }

    @SuppressWarnings("unchecked")
    public static <T extends SyncSession> RequestCache<T> get() {
        RequestCache<T> req = (RequestCache<T>) cache.get();
        if (req == null) throw new RuntimeException("RequestCacheHolder not init");
        return req;
    }

    public static void clear() {
        cache.remove();
    }


    private RequestCacheHolder() {
    }

}


