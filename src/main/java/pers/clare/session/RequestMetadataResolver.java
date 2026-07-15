package pers.clare.session;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Locale.*;

@Slf4j
@UtilityClass
public class RequestMetadataResolver {

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
        if (code == null || code.isEmpty() || locale == null) return;
        supportLocaleMap.put(code.toLowerCase(), locale);
    }

    public static Locale getLocale(String lang) {
        if (lang != null && !lang.isEmpty()) {
            try {
                List<LanguageRange> ranges = LanguageRange.parse(lang);
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
                log.error(e.getMessage(), e);
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
        if (clientIp == null || clientIp.isEmpty() || "unknown".equalsIgnoreCase(clientIp)) {
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

}
