package pers.clare.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class RequestCacheTest {

    static {
        RequestCache.setDefaultLocale(Locale.forLanguageTag("zh-TW"));
        RequestCache.addSupportLocale(Locale.ENGLISH, Locale.TRADITIONAL_CHINESE, Locale.SIMPLIFIED_CHINESE);
        RequestCache.addMappingLocale("zh", Locale.forLanguageTag("zh-TW"));
    }

    void localeEquals(Locale locale, String language) {
        Locale locale2 = RequestCache.getLocale(language);
        assertEquals(locale.toLanguageTag(), locale2.toLanguageTag());
    }

    @Test
    void getLocale() {
        localeEquals(Locale.forLanguageTag("en"), "en,zh;q=0.9,fas;q=0.5,*;q=1");
        localeEquals(Locale.forLanguageTag("zh-CN"), "zh-CN;q=0.9,fas;q=0.5");
        localeEquals(Locale.forLanguageTag("zh-TW"), "zh-CN;q=0.9,fas;q=0.5,*;q=1");
        localeEquals(Locale.forLanguageTag("zh-TW"), "zh;q=0.9,fas;q=0.5,en;q=0.3");
        localeEquals(Locale.forLanguageTag("zh-TW"), "en;q=0.9,fas;q=0.5,*;q=1");
        localeEquals(Locale.forLanguageTag("en"), "zh-TW;q=0.9,fas;q=0.5,en;q=1");

        RequestCache.removeSupportLocale(Locale.ENGLISH, Locale.TRADITIONAL_CHINESE, Locale.SIMPLIFIED_CHINESE);
        RequestCache.addSupportLocale(Locale.forLanguageTag("fas"));
        localeEquals(Locale.forLanguageTag("fas"), "zh-TW;q=0.9,fas;q=0.5,en;q=1");
    }
}
