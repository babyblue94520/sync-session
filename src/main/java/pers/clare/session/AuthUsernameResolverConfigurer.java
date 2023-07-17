package pers.clare.session;

import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pers.clare.session.support.auth.AuthArgumentResolver;

import java.util.List;

public class AuthUsernameResolverConfigurer implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(
            List<HandlerMethodArgumentResolver> argumentResolvers
    ) {
        argumentResolvers.add(new AuthArgumentResolver());
    }
}
