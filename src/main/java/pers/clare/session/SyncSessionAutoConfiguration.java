package pers.clare.session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import javax.sql.DataSource;

@Configuration
@ConditionalOnBean(SyncSessionConfiguration.class)
public class SyncSessionAutoConfiguration {

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
            SyncSessionService<?> syncSessionService
    ) {
        return new SyncSessionFilter(syncSessionService);
    }
}
