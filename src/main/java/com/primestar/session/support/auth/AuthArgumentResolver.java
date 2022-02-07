package com.primestar.session.support.auth;

import com.primestar.session.RequestCache;
import com.primestar.session.RequestCacheHolder;
import com.primestar.session.SyncSession;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;


@Service
public class AuthArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter methodParameter) {
        return methodParameter.getParameterAnnotation(AuthUsername.class) != null;
    }

    @Override
    public Object resolveArgument(
            MethodParameter methodParameter
            , ModelAndViewContainer modelAndViewContainer
            , NativeWebRequest nativeWebRequest
            , WebDataBinderFactory webDataBinderFactory
    ) throws Exception {
        RequestCache<SyncSession> requestCache = RequestCacheHolder.get();
        SyncSession syncSession = requestCache.getSession();
        if (syncSession == null) return null;
        String username = syncSession.getUsername();
        if (username == null) return null;

        Class<?> type = methodParameter.getParameterType();
        if (Long.class.isAssignableFrom(type)) {
            return Long.valueOf(username);
        } else if (Integer.class.isAssignableFrom(type)) {
            return Integer.valueOf(username);
        }
        return username;
    }
}
