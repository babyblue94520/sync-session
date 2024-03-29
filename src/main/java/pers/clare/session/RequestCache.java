package pers.clare.session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;
import pers.clare.session.configuration.SyncSessionProperties;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Locale.*;

@SuppressWarnings("unused")
public class RequestCache<T extends SyncSession> {
    private static final Logger log = LogManager.getLogger();

    private static final ConcurrentMap<String, String> ipHeaderNameMap = new ConcurrentHashMap<>();

    private static final ConcurrentMap<String, Locale> supportLocaleMap = new ConcurrentHashMap<>();

    private static Locale defaultLocale = Locale.getDefault();

    static {
        addSupportLocale(ENGLISH, TRADITIONAL_CHINESE, SIMPLIFIED_CHINESE);
        addIpHeader("x-forwarded-for", "Proxy-Client-IP", "WL-Proxy-Client-IP", "X-Real-IP");
    }

    public static void setDefaultLocale(Locale locale) {
        if (locale == null) return;
        defaultLocale = locale;
    }

    public static void addIpHeader(String... names) {
        for (String name : names) {
            ipHeaderNameMap.put(name, name);
        }
    }

    public static void removeIpHeader(String... names) {
        for (String name : names) {
            ipHeaderNameMap.remove(name, name);
        }
    }

    public static void addSupportLocale(Locale... locales) {
        for (Locale locale : locales) {
            if (locale == null) continue;
            supportLocaleMap.putIfAbsent(locale.toLanguageTag().toLowerCase(), locale);
        }
    }

    public static void removeSupportLocale(Locale... locales) {
        for (Locale locale : locales) {
            if (locale == null) continue;
            supportLocaleMap.remove(locale.toLanguageTag().toLowerCase());
        }
    }

    /**
     * add language code mapping locale. ex. zh mapping zh-TW, en-US mapping en.
     */
    public static void addMappingLocale(String code, Locale locale) {
        if (code == null || code.length() == 0 || locale == null) return;
        supportLocaleMap.put(code.toLowerCase(), locale);
    }

    public static Locale getLocale(String lang) {
        if (lang != null && lang.length() > 0) {
            try {
                List<LanguageRange> ranges = Locale.LanguageRange.parse(lang);
                for (LanguageRange range : ranges) {
                    // range is lowercase
                    String code = range.getRange();
                    if (Objects.equals("*", code)) {
                        break;
                    }
                    Locale locale = supportLocaleMap.get(code);
                    if (locale != null) return locale;
                }
            } catch (Exception e) {
                log.error(e);
            }
        }
        return defaultLocale;
    }

    public static String getClientIp(HttpServletRequest request) {
        String clientIp;
        for (String name : ipHeaderNameMap.keySet()) {
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
            return Collections.emptyMap();
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
        removeSession();
    }

    void invalidate(T session) {
        if (session == null) return;
        session.valid = false;
        if (this.session == null) this.session = getSession();
        if (this.session != null && Objects.equals(this.session.id, session.id)) {
            removeSession();
            this.session.valid = false;
        }
        sessionService.invalidate(session);
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
            session = sessionService.find(findSessionId());
        } else {
            if (session.valid) {
                return session.getId();
            }
        }
        if (session == null || !session.valid) {
            if (auto) {
                session = sessionService.create(accessTime, getUserAgent(), getClientIp());
                responseSession();
            } else {
                return null;
            }
        }

        return session.getId();
    }

    public Cookie getCookie(String name) {
        if (name == null) return null;
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
            parameterMap = convert(getParametersMap());
        }
        return parameterMap;
    }

    public String getHeader(String name) {
        return request.getHeader(name);
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

    public Locale getLocale() {
        if (locale != null) return locale;
        return (locale = getLocale(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE)));
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

    protected String findSessionId() {
        SyncSessionProperties properties = sessionService.getProperties();
        String name = properties.getName();
        switch (properties.getMode()) {
            case Cookie:
                Cookie cookie = getCookie(name);
                return cookie == null ? null : cookie.getValue();
            case Header:
                return getHeader(name);
            case QueryString:
                return getParameterMap().get(name);
        }
        return null;
    }

    protected void responseSession(){
        SyncSessionProperties properties = sessionService.getProperties();
        String name = properties.getName();
        switch (properties.getMode()) {
            case Cookie:
                addCookie(ResponseCookie.from(name, session.getId())
                        .httpOnly(true)
                        .path("/"));
                break;
            case Header:
            case QueryString:
                response.addHeader(name, session.getId());
                break;
        }
    }

    protected void removeSession(){
        SyncSessionProperties properties = sessionService.getProperties();
        String name = properties.getName();
        switch (properties.getMode()) {
            case Cookie:
                addCookie(ResponseCookie.from(name, "")
                        .httpOnly(true)
                        .maxAge(0)
                        .path("/"));
                break;
            case Header:
            case QueryString:
                response.addHeader(name, "");
                break;
        }
    }

}
