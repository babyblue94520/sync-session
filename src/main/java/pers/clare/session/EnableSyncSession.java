package pers.clare.session;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import({SyncSessionConfiguration.class, AuthUsernameResolverConfigurer.class})
public @interface EnableSyncSession {
}
