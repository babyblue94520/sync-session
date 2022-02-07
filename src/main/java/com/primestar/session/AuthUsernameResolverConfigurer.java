package com.primestar.session;

import com.primestar.session.support.auth.AuthArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

public class AuthUsernameResolverConfigurer implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(
            List<HandlerMethodArgumentResolver> argumentResolvers
    ) {
        argumentResolvers.add(new AuthArgumentResolver());
    }
}
