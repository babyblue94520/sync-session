package pers.clare.session.configuration;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import({SyncSessionConfiguration.class})
public @interface EnableSyncSession {
}
