package pers.clare.session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;
import pers.clare.session.service.SessionIdTransportService;
import pers.clare.session.service.SyncSession;
import pers.clare.session.service.SyncSessionService;

import java.util.Locale;
import java.util.Map;

@SuppressWarnings("unused")
public class SyncSessionRequestContext<T extends SyncSession> {

    private final SyncSessionService<T> sessionService;

    private final SessionIdTransportService sessionIdTransportService;

    @Getter
    private final HttpServletRequest request;

    @Getter
    private final HttpServletResponse response;

    @Getter
    private final Long accessTime;

    @Getter
    private final String remoteIp;

    private String clientIp;

    private String userAgent;

    private String url;

    private String origin;

    private String referer;

    private String lang;

    private Locale locale;

    private Map<String, String> parameterMap;

    private Map<String, String[]> parametersMap;

    private T currentSession;

    private Cookie sessionCookie;

    @Setter
    private boolean ping = false;

    SyncSessionRequestContext(
            HttpServletRequest request,
            HttpServletResponse response,
            SyncSessionService<T> sessionService,
            SessionIdTransportService sessionIdTransportService
    ) {
        this.request = request;
        this.response = response;
        this.sessionService = sessionService;
        this.sessionIdTransportService = sessionIdTransportService;
        this.accessTime = System.currentTimeMillis();
        this.remoteIp = request.getRemoteAddr();
    }

    public void invalidate() {
        if (currentSession == null)
            currentSession = getSession();
        if (currentSession == null)
            return;
        invalidate(currentSession);
    }

    public void invalidate(T session) {
        if (session == null)
            return;
        sessionService.invalidate(session);
    }

    public void refreshSession() {
        if (currentSession == null)
            getSession();
        if (currentSession == null)
            return;

        if (currentSession.isValid()) {
            boolean isNewSession = currentSession.isNew();
            if (!ping) {
                currentSession.setLastAccessTime(accessTime);
            }
            sessionService.refresh(currentSession);
            if (!currentSession.isValid()) {
                removeSession();
                return;
            }
            if (isNewSession) {
                sessionIdTransportService.write(request, response, currentSession.getId());
            }
        } else {
            removeSession();
        }
    }

    public T getSession() {
        return getSession(false);
    }

    public T getSession(boolean auto) {
        if (currentSession != null && currentSession.isValid()) {
            return currentSession;
        }
        getSessionId(auto);
        return currentSession;
    }

    public String getSessionId() {
        return getSessionId(false);
    }

    public String getSessionId(boolean auto) {
        if (currentSession == null) {
            currentSession = sessionService.find(sessionIdTransportService.find(request));
        } else {
            if (currentSession.isValid()) {
                return currentSession.getId();
            }
        }
        if (currentSession == null || !currentSession.isValid()) {
            if (auto) {
                currentSession = sessionService.create(accessTime, getUserAgent(), getClientIp());
            } else {
                return null;
            }
        }
        return currentSession.getId();
    }

    public Cookie getCookie(String name) {
        if (name == null)
            return null;
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie;
            }
        }
        return null;
    }

    public void removeCookie(String name) {
        addCookie(ResponseCookie.from(name, "")
                .httpOnly(true)
                .maxAge(0)
                .path("/"));
    }

    public Map<String, String[]> getParametersMap() {
        if (parametersMap == null) {
            parametersMap = request.getParameterMap();
        }
        return parametersMap;
    }

    public Map<String, String> getParameterMap() {
        if (parameterMap == null) {
            parameterMap = RequestMetadataResolver.convert(getParametersMap());
        }
        return parameterMap;
    }

    public String getHeader(String name) {
        return request.getHeader(name);
    }

    public String getClientIp() {
        if (clientIp == null) {
            clientIp = RequestMetadataResolver.getClientIp(request);
        }
        return clientIp;
    }

    public String getUserAgent() {
        if (userAgent == null) {
            userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        }
        return userAgent;
    }

    public String getUrl() {
        if (url == null) {
            url = request.getRequestURL().toString();
        }
        return url;
    }

    public String getOrigin() {
        if (origin == null) {
            origin = request.getHeader(HttpHeaders.ORIGIN);
        }
        return origin;
    }

    public String getReferer() {
        if (referer == null) {
            referer = request.getHeader(HttpHeaders.REFERER);
        }
        return referer;
    }

    public Locale getLocale() {
        if (locale == null) {
            locale = RequestMetadataResolver.getLocale(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE));
        }
        return locale;
    }

    public void addCookie(ResponseCookie.ResponseCookieBuilder cookieBuilder) {
        String o = getOrigin();
        // process https only
        if (StringUtils.hasLength(o)
                && !(getUrl().startsWith(o) && getUrl().startsWith("/", o.length()))) {
            cookieBuilder
                    .secure(true)
                    .sameSite("none");
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder.build().toString());
    }

    public long getSessionTimeout(boolean ping) {
        SyncSession session = getSession(false);
        long timeout = 0;
        if (session != null) {
            setPing(ping);
            long lastAccessTime = session.getLastAccessTime();
            if (lastAccessTime == 0) {
                timeout = session.getMaxInactiveInterval();
            } else {
                timeout = session.getLastAccessTime() + session.getMaxInactiveInterval() - getAccessTime();
            }
        }
        return timeout < 0 ? 0 : timeout;
    }

    protected void removeSession() {
        sessionIdTransportService.clear(request, response);
    }

}
