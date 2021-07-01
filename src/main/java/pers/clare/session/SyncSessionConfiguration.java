package pers.clare.session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.lang.Nullable;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pers.clare.session.support.auth.AuthArgumentResolver;

import javax.sql.DataSource;
import java.util.List;

@EnableConfigurationProperties(SyncSessionProperties.class)
public class SyncSessionConfiguration implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(
            List<HandlerMethodArgumentResolver> argumentResolvers
    ) {
        argumentResolvers.add(new AuthArgumentResolver());
    }

    @Bean
    @Autowired(required = false)
    @ConditionalOnMissingBean(SyncSessionService.class)
    public SyncSessionService<SyncSession> syncSessionService(
            DataSource dataSource
            , SyncSessionProperties properties
            , @Nullable SyncSessionEventService sessionEventService
    ) {
        return new SyncSessionServiceImpl<>(properties, dataSource, sessionEventService);
    }

    @Bean
    @ConditionalOnMissingBean(value = SyncSessionFilter.class)
    public SyncSessionFilter syncSessionFilter(
            SyncSessionService syncSessionService
    ) {
        return new SyncSessionFilter(syncSessionService);
    }
}
