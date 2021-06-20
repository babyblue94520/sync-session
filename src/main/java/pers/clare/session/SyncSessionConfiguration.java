package pers.clare.session;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "sync-session.dataSourceRef")
@EnableConfigurationProperties(SyncSessionProperties.class)
public class SyncSessionConfiguration implements ImportAware, BeanFactoryAware {

    private AnnotationAttributes annotationAttributes;
    private BeanFactory beanFactory;


    @Bean
    @ConditionalOnMissingBean(SyncSessionService.class)
    public SyncSessionService<SyncSession> syncSessionService(
            SyncSessionProperties properties
    ) {
        DataSource dataSource = (DataSource) this.beanFactory.getBean(annotationAttributes.getString("dataSourceRef"));
        return new SyncSessionServiceImpl<>(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(value = SyncSessionFilter.class)
    public SyncSessionFilter syncSessionFilter(
            SyncSessionService syncSessionService
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
