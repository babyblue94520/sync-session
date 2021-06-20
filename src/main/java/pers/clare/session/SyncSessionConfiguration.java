package pers.clare.session;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(SyncSessionProperties.class)
public class SyncSessionConfiguration implements ImportAware, BeanFactoryAware {

    private AnnotationAttributes annotationAttributes;
    private BeanFactory beanFactory;

    public SyncSessionConfiguration() {
        System.out.println("SyncSessionConfiguration");
    }

    @Bean
    @Primary
    @Autowired(required = false)
    public SyncSessionService<SyncSession> syncSessionService(
            SyncSessionProperties syncSessionProperties
            , @Nullable SyncSessionEventService syncSessionEventService
    ) {
        DataSource dataSource = (DataSource) this.beanFactory.getBean(annotationAttributes.getString("dataSourceRef"));
        return new SyncSessionServiceImpl<>(
                (Class<? extends SyncSession>)annotationAttributes.getClass("sessionClass")
                , syncSessionProperties
                , dataSource
                , syncSessionEventService
        );
    }

    @Bean
    @ConditionalOnMissingBean(value = SyncSessionFilter.class)
    public SyncSessionFilter syncSessionFilter(
            SyncSessionService<SyncSession> syncSessionService
    ) {
        return new SyncSessionFilter(syncSessionService);
    }

    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        this.annotationAttributes = AnnotationAttributes
                .fromMap(importMetadata.getAnnotationAttributes(EnableSyncSession.class.getName()));
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
}
