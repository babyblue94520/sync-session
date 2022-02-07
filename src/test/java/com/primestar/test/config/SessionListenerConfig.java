package com.primestar.test.config;

import com.primestar.session.SyncSessionService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SessionListenerConfig implements InitializingBean {
    @Autowired
    private SyncSessionService<?> syncSessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${server.port:}")
    private String port;

    @Override
    public void afterPropertiesSet() throws Exception {
        syncSessionService.addInvalidateListeners((id, username, type) -> {
            jdbcTemplate.update("insert into log values(?,?,?)", port, id, type);
        });
    }
}
