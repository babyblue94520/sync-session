package pers.clare.session.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;
import pers.clare.session.configuration.SyncSessionProperties;
import pers.clare.session.constant.SessionMode;

import java.util.Objects;

public class DefaultSessionIdTransportService implements SessionIdTransportService {

    @Setter(onMethod = @__({@Autowired}))
    private SyncSessionProperties properties;

    @Override
    public String find(HttpServletRequest request) {
        String name = properties.getName();
        if (Objects.equals(properties.getMode(), SessionMode.Cookie)) {
            Cookie cookie = getCookie(request, name);
            return cookie == null ? null : normalizeSessionId(cookie.getValue());
        } else if (Objects.equals(properties.getMode(), SessionMode.Header)) {
            return normalizeSessionId(request.getHeader(name));
        }
        return null;
    }

    @Override
    public void write(HttpServletRequest request, HttpServletResponse response, String sessionId) {
        String name = properties.getName();
        if (Objects.equals(properties.getMode(), SessionMode.Cookie)) {
            addCookie(request, response, ResponseCookie.from(name, sessionId)
                    .httpOnly(true)
                    .path("/"));
        } else if (Objects.equals(properties.getMode(), SessionMode.Header)) {
            response.addHeader(name, sessionId);
        }
    }

    @Override
    public void clear(HttpServletRequest request, HttpServletResponse response) {
        String name = properties.getName();
        if (Objects.equals(properties.getMode(), SessionMode.Cookie)) {
            addCookie(request, response, ResponseCookie.from(name, "")
                    .httpOnly(true)
                    .maxAge(0)
                    .path("/"));
        } else if (Objects.equals(properties.getMode(), SessionMode.Header)) {
            response.addHeader(name, "");
        }
    }

    private static Cookie getCookie(HttpServletRequest request, String name) {
        if (name == null) return null;
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie;
            }
        }
        return null;
    }

    private static void addCookie(
            HttpServletRequest request,
            HttpServletResponse response,
            ResponseCookie.ResponseCookieBuilder cookieBuilder
    ) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        String url = request.getRequestURL().toString();
        // process https only
        if (StringUtils.hasLength(origin)
            && !(url.startsWith(origin) && url.startsWith("/", origin.length()))) {
            cookieBuilder
                    .secure(true)
                    .sameSite("none");
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder.build().toString());
    }

    private static String normalizeSessionId(String sessionId) {
        return StringUtils.hasLength(sessionId) ? sessionId : null;
    }
}
