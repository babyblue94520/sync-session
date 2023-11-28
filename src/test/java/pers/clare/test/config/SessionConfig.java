package pers.clare.test.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import pers.clare.session.event.SyncSessionEventService;
import pers.clare.test.session.SyncSessionEventServiceImpl;

@Configuration
public class SessionConfig {
    @Primary
    @Bean
    public SyncSessionEventService syncSessionEventService() {
        return new SyncSessionEventServiceImpl();
    }
}
