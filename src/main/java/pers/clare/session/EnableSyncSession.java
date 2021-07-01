package pers.clare.session;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(SyncSessionConfiguration.class)
@Configuration
public @interface EnableSyncSession {
    @AliasFor(
            annotation = Configuration.class
    )
    String value() default "";
}
