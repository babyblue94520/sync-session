package pers.clare.test.config;

import pers.clare.session.SyncSessionEventService;
import pers.clare.session.SyncSessionProperties;
import pers.clare.session.SyncSessionService;
import pers.clare.test.session.SyncSessionEventServiceImpl;
import pers.clare.test.session.TokenSession;
import pers.clare.test.session.TokenSessionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;

import javax.sql.DataSource;

@Configuration
public class SessionConfig {
    @Primary
    @Bean
    public SyncSessionEventService syncSessionEventService() {
        return new SyncSessionEventServiceImpl();
    }

    @Primary
    @Bean
    public SyncSessionService<TokenSession> syncSessionService(
            SyncSessionProperties properties
            , DataSource dataSource
            , @Nullable SyncSessionEventService sessionEventService
    ) {
        return new TokenSessionService(properties, dataSource, sessionEventService);
    }
}
