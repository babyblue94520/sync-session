package pers.clare.test.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SessionListenerConfig implements InitializingBean {

    @Value("${server.port:}")
    private String port;

    @Value("${listen:false}")
    private Boolean listen = false;

    @Override
    public void afterPropertiesSet() {
    }

}
