package pers.clare.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.experimental.UtilityClass;
import pers.clare.session.exception.SyncSessionException;
import pers.clare.session.service.SessionIdTransportService;
import pers.clare.session.service.SyncSession;
import pers.clare.session.service.SyncSessionService;

@UtilityClass
@SuppressWarnings("unused")
public class SyncSessionRequestContextHolder {

    private static ThreadLocal<SyncSessionRequestContext<? extends SyncSession>> contextHolder = new ThreadLocal<>();

    public static void setContextHolder(ThreadLocal<SyncSessionRequestContext<? extends SyncSession>> contextHolder) {
        SyncSessionRequestContextHolder.contextHolder = contextHolder;
    }

    public static void set(SyncSessionRequestContext<? extends SyncSession> context) {
        contextHolder.set(context);
    }

    public static <T extends SyncSession> SyncSessionRequestContext<T> init(
            HttpServletRequest request,
            HttpServletResponse response,
            SyncSessionService<T> sessionService,
            SessionIdTransportService sessionIdTransportService
    ) {
        SyncSessionRequestContext<T> context = new SyncSessionRequestContext<>(request, response, sessionService, sessionIdTransportService);
        contextHolder.set(context);
        return context;
    }

    @SuppressWarnings("unchecked")
    public static <T extends SyncSession> SyncSessionRequestContext<T> get() {
        SyncSessionRequestContext<T> context = (SyncSessionRequestContext<T>) contextHolder.get();
        if (context == null) throw new SyncSessionException("SyncSessionRequestContextHolder not init");
        return context;
    }

    public static void clear() {
        contextHolder.remove();
    }

}
