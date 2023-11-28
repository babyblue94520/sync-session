package pers.clare.session.configuration;

import org.springframework.context.annotation.Import;
import pers.clare.session.configuration.SyncSessionConfiguration;
import pers.clare.session.support.auth.AuthUsernameResolverConfigurer;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import({SyncSessionConfiguration.class, AuthUsernameResolverConfigurer.class})
public @interface EnableSyncSession {
}
