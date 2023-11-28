package pers.clare.session.util;

import pers.clare.session.SyncSession;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.UUID;

public class SessionUtil {

    public static Class<?> getSessionClass(Class<?> clazz) {

        Type type = clazz.getGenericSuperclass();
        Type[] types  = clazz.getGenericInterfaces();
        if (type instanceof ParameterizedType) {
            Type actualType = ((ParameterizedType) type).getActualTypeArguments()[0];
            if (actualType instanceof Class) {
                return (Class<?>) actualType;
            }
        }
        return SyncSession.class;
    }

    public static String generateId() {
        return generateId(UUID.randomUUID());
    }

    public static String generateId(UUID uuid) {
        return (digits(uuid.getMostSignificantBits() >> 32, 8) +
                digits(uuid.getMostSignificantBits() >> 16, 4) +
                digits(uuid.getMostSignificantBits(), 4) +
                digits(uuid.getLeastSignificantBits() >> 48, 4) +
                digits(uuid.getLeastSignificantBits(), 12));
    }

    private static String digits(long val, int digits) {
        long hi = 1L << (digits * 4);
        return Long.toHexString(hi | (val & (hi - 1))).substring(1);
    }
}
