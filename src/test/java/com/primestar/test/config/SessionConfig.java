package com.primestar.test.config;

import com.primestar.session.SyncSessionEventService;
import com.primestar.session.SyncSessionProperties;
import com.primestar.session.SyncSessionService;
import com.primestar.test.session.SyncSessionEventServiceImpl;
import com.primestar.test.session.TokenSession;
import com.primestar.test.session.TokenSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.lang.Nullable;

import javax.sql.DataSource;

@Configuration
public class SessionConfig{

    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate stringRedisTemplate = new StringRedisTemplate();
        stringRedisTemplate.setConnectionFactory(connectionFactory);
        return stringRedisTemplate;
    }

    @Bean
    @ConditionalOnMissingBean(RedisMessageListenerContainer.class)
    public RedisMessageListenerContainer container(
            RedisConnectionFactory connectionFactory
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setRecoveryInterval(5000L);
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    @Primary
    @Bean
    @ConditionalOnBean({
            StringRedisTemplate.class
            , RedisMessageListenerContainer.class
    })
    public SyncSessionEventService syncSessionEventService() {
        return new SyncSessionEventServiceImpl();
    }


    @Primary
    @Bean
    @Autowired(required = false)
    public SyncSessionService<TokenSession> syncSessionService(
            SyncSessionProperties properties
            , DataSource dataSource
            , @Nullable SyncSessionEventService sessionEventService
    ) {
        return new TokenSessionService(properties, dataSource, sessionEventService);
    }

}
