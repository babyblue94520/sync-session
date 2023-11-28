package pers.clare.session.configuration;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import pers.clare.session.*;
import pers.clare.session.constant.StoreType;
import pers.clare.session.event.SyncSessionEventService;
import pers.clare.session.filter.SyncSessionFilter;
import pers.clare.session.SyncSessionDataSourceStore;
import pers.clare.session.SyncSessionLocalStore;

import javax.sql.DataSource;

@Configuration
@ConditionalOnBean(SyncSessionConfiguration.class)
public class SyncSessionAutoConfiguration {

    @Bean
    @Autowired(required = false)
    @ConditionalOnMissingBean(SyncSessionStore.class)
    public SyncSessionStore<?> syncSessionStore(
            SyncSessionProperties properties
            , DefaultListableBeanFactory beanFactory
    ) {
        if (properties.getStore() == StoreType.Local) {
            return new SyncSessionLocalStore<>(properties.getLocal(), properties.getClazz());
        } else {
            String beanName = properties.getDs().getBeanName();
            DataSource dataSource;
            if (StringUtils.hasLength(beanName)) {
                dataSource = beanFactory.getBean(beanName, DataSource.class);
            } else {
                dataSource = beanFactory.getBean(DataSource.class);
            }
            return new SyncSessionDataSourceStore<>(dataSource, properties.getDs(), properties.getClazz());
        }
    }

    @Bean
    @Autowired(required = false)
    @ConditionalOnMissingBean(SyncSessionService.class)
    public SyncSessionService<?> syncSessionService(
            SyncSessionProperties properties
            , SyncSessionStore<?> syncSessionStore
            , @Nullable SyncSessionEventService sessionEventService
    ) {
        if (properties.getStore() == StoreType.Local) {
            return new SyncSessionServiceImpl<>(properties, syncSessionStore);
        } else {
            return new SyncSessionServiceImpl<>(properties, syncSessionStore, sessionEventService);
        }
    }

    @Bean
    @ConditionalOnMissingBean(value = SyncSessionFilter.class)
    public SyncSessionFilter syncSessionFilter(
            SyncSessionService<?> syncSessionService
    ) {
        return new SyncSessionFilter(syncSessionService);
    }
}
