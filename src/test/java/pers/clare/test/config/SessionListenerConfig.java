package pers.clare.test.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pers.clare.session.SyncSessionService;
import pers.clare.test.session.TokenSession;

@Component
public class SessionListenerConfig implements InitializingBean {
    @Autowired
    private SyncSessionService<TokenSession> syncSessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${server.port:}")
    private String port;

    @Value("${listen:false}")
    private Boolean listen = false;

    @Override
    public void afterPropertiesSet() throws Exception {
        if(listen){
            syncSessionService.addInvalidateListeners((id, username, type) -> {
                jdbcTemplate.update("insert into log values(?,?,?)", port, id, type);
            });
        }
    }
}
