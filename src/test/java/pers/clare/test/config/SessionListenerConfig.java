package pers.clare.test.config;

import pers.clare.session.SyncSessionService;
import pers.clare.test.session.TokenSession;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SessionListenerConfig implements InitializingBean {
    @Autowired
    private SyncSessionService<TokenSession> syncSessionService;

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
