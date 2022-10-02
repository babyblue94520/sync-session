package pers.clare.session;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SyncSessionProperties.class)
public class SyncSessionConfiguration {
}
