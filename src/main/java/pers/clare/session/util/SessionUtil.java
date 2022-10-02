package pers.clare.session.util;

import pers.clare.session.SyncSession;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class SessionUtil {

    public static Class<?> getSessionClass(Class<?> clazz) {
        Type type = clazz.getGenericSuperclass();
        if (type instanceof ParameterizedType) {
            Type actualType = ((ParameterizedType) type).getActualTypeArguments()[0];
            if (actualType instanceof Class) {
                return (Class<?>) actualType;
            }
        }
        return SyncSession.class;
    }
}
