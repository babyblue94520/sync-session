package pers.clare.session;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

@SuppressWarnings("unused")
public class RequestCache<T extends SyncSession> {
    private static final Set<String> ipHeaderNames = new HashSet<>();

    public static final Set<Locale> supportLocales = new HashSet<>();

    static {
        supportLocales.add(Locale.ENGLISH);
        supportLocales.add(Locale.TRADITIONAL_CHINESE);
        supportLocales.add(Locale.SIMPLIFIED_CHINESE);

        ipHeaderNames.add("x-forwarded-for");
        ipHeaderNames.add("Proxy-Client-IP");
        ipHeaderNames.add("WL-Proxy-Client-IP");
        ipHeaderNames.add("X-Real-IP");
    }

    public synchronized static void addIpHeader(String name) {
        ipHeaderNames.add(name);
    }

    public synchronized static void addSupportLocale(Locale locale) {
        supportLocales.add(locale);
    }

    public static Locale getLocale(String lang) {
        return Locale.lookup(Locale.LanguageRange.parse(lang), supportLocales);
    }

    public static String getClientIp(HttpServletRequest request) {
        String clientIp;
        for (String name : ipHeaderNames) {
            clientIp = getFirstIp(request.getHeader(name));
            if (clientIp != null) {
                return clientIp;
            }
        }
        return request.getRemoteAddr();
    }

    public static String getFirstIp(String clientIp) {
        if (clientIp == null || clientIp.length() == 0 || "unknown".equalsIgnoreCase(clientIp)) {
            return null;
        }
        char[] cs = clientIp.toCharArray();
        char c;
        for (int i = 0, l = cs.length; i < l; i++) {
            c = cs[i];
            if (c == ',') {
                return new String(cs, 0, i);
            }
        }
        return clientIp;
    }

    public static Map<String, String> convert(Map<String, String[]> map) {
        if (map == null) {
            return null;
        } else {
            Map<String, String> newMap = new HashMap<>();
            for (Map.Entry<String, String[]> entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    newMap.put(entry.getKey(), null);
                } else {
                    newMap.put(entry.getKey(), String.join(",", entry.getValue()));
                }
            }
            return newMap;
        }
    }

    private SyncSessionService<T> sessionService;

    private HttpServletRequest request;

    private HttpServletResponse response;

    private Long accessTime;

    private String remoteIp;

    private String clientIp;

    private String userAgent;

    private String url;

    private String origin;

    private String referer;

    private String lang;

    private Locale locale;

    private Map<String, String> parameterMap;

    private Map<String, String[]> parametersMap;

    private T session;

    private Cookie sessionCookie;

    private boolean ping = false;

    private boolean updateSession = false;

    public void save() {
        updateSession = true;
    }


    RequestCache() {
    }

    void init(HttpServletRequest request, HttpServletResponse response, SyncSessionService<T> sessionService) {
        this.request = request;
        this.response = response;
        this.sessionService = sessionService;
        this.accessTime = System.currentTimeMillis();
        this.remoteIp = request.getRemoteAddr();
    }

    public void invalidate() {
        if (session == null) session = getSession();
        if (session == null) return;
        session.valid = false;
        sessionService.invalidate(session);
        removeSessionCookie();
    }

    public void refreshSession() {
        if (session == null) getSession();
        if (session == null) return;
        if (session.valid) {
            if (!ping) {
                session.setLastAccessTime(accessTime);
            }
            if (updateSession) {
                sessionService.update(session);
                updateSession = false;
            }
        }
    }

    public void finish() {
        this.request = null;
        this.response = null;
        this.sessionService = null;
        this.accessTime = null;
        this.remoteIp = null;
        this.clientIp = null;
        this.userAgent = null;
        this.origin = null;
        this.url = null;
        this.referer = null;
        this.lang = null;
        this.locale = null;
        this.parametersMap = null;
        this.parameterMap = null;
        this.session = null;
        this.sessionCookie = null;
        this.ping = false;
        this.updateSession = false;
    }

    public void setPing(boolean ping) {
        this.ping = ping;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public HttpServletResponse getResponse() {
        return response;
    }

    public Long getAccessTime() {
        return accessTime;
    }

    public String getRemoteIp() {
        return remoteIp;
    }

    public T getSession() {
        return getSession(false);
    }

    public T getSession(boolean auto) {
        if (session != null && session.valid) {
            return session;
        }
        getSessionId(auto);
        return session;
    }

    public String getSessionId() {
        return getSessionId(false);
    }

    public String getSessionId(boolean auto) {
        if (session == null) {
            Cookie cookie = getSessionCookie();
            String id = cookie == null ? null : cookie.getValue();
            session = sessionService.find(id);
        } else {
            if (session.valid) {
                return session.getId();
            }
        }
        if (session == null || !session.valid) {
            if (auto) {
                session = sessionService.create(accessTime, getUserAgent(), getClientIp());
                addCookie(ResponseCookie.from(sessionService.getProperties().getCookieName(), session.getId())
                        .httpOnly(true)
                        .path("/"));
            } else {
                return null;
            }
        }

        return session.getId();
    }

    public Cookie getCookie(String name) {
        if (name == null) {
            return null;
        }
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

    public Cookie getSessionCookie() {
        if (sessionCookie != null) {
            return sessionCookie;
        }
        sessionCookie = getCookie(sessionService.getProperties().getCookieName());
        return sessionCookie;
    }

    private void removeSessionCookie() {
        addCookie(ResponseCookie.from(sessionService.getProperties().getCookieName(), "")
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
            parameterMap = convert(getParametersMap());
        }
        return parameterMap;
    }

    public String getClientIp() {
        if (clientIp == null) {
            clientIp = getClientIp(request);
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

    public String getLang() {
        if (lang == null) {
            lang = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        }
        return lang;
    }

    public Locale getLocale() {
        if (locale != null) return locale;
        if (getLang() == null) return null;
        return locale = getLocale(lang);
    }

    public void addCookie(ResponseCookie.ResponseCookieBuilder cookieBuilder) {
        String origin = getOrigin();
        // 處理跨域
        if (StringUtils.hasLength(origin)
                && !(getUrl().startsWith(origin) && getUrl().startsWith("/", origin.length()))
        ) {
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
}
