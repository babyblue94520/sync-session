package pers.clare.session.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pers.clare.session.constant.StoreType;
import pers.clare.session.filter.SyncSessionFilter;
import pers.clare.session.service.*;
import pers.clare.session.store.SyncSessionDataSourceStore;
import pers.clare.session.store.SyncSessionLocalStore;
import pers.clare.session.store.SyncSessionStore;

@Configuration
@ConditionalOnBean(SyncSessionConfiguration.class)
public class SyncSessionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SyncSessionStore.class)
    public SyncSessionStore<?> syncSessionStore(
            SyncSessionProperties properties
    ) {
        if (properties.getStore() == StoreType.Local) {
            return new SyncSessionLocalStore<>();
        } else {
            return new SyncSessionDataSourceStore<>();
        }
    }

    @Bean
    @ConditionalOnMissingBean(SyncSessionService.class)
    public SyncSessionService<?> syncSessionService() {
        return new SyncSessionServiceImpl<>();
    }

    @Bean
    @ConditionalOnMissingBean(SessionIdTransportService.class)
    public SessionIdTransportService sessionIdTransportService() {
        return new DefaultSessionIdTransportService();
    }

    @Bean
    @ConditionalOnMissingBean(value = SyncSessionFilter.class)
    public SyncSessionFilter syncSessionFilter() {
        return new SyncSessionFilter();
    }
}
