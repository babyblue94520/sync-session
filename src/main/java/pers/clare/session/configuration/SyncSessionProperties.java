package pers.clare.session.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import pers.clare.session.SyncSession;
import pers.clare.session.constant.SessionMode;
import pers.clare.session.constant.StoreType;

import java.time.Duration;


@Setter
@Getter
@ConfigurationProperties(prefix = SyncSessionProperties.PREFIX)
public class SyncSessionProperties {
    public static final String PREFIX = "sync-session";

    /**
     * Cookie name
     */
    private String name = "SSESSIONID";

    private SessionMode mode = SessionMode.Cookie;

    private StoreType store = StoreType.DataSource;

    /**
     * Session timeout
     */
    private Duration timeout = Duration.ofMinutes(30);

    private Long batchInvalidateCount = 100L;

    /**
     * Session class
     */
    private Class<? extends SyncSession> clazz = SyncSession.class;

    /**
     * Store locally.
     */
    @NestedConfigurationProperty
    private LocalProperties local = new LocalProperties();

    /**
     * Stored by DataSource.
     */
    @NestedConfigurationProperty
    private DSProperties ds = new DSProperties();

    @Setter
    @Getter
    public static class LocalProperties {
        /**
         * Whether to persist the session.
         */
        private boolean persistence = false;

        /**
         * Persistent storage path.
         */
        private String path = "temp";

        private String fileName = "session.ser";

    }

    @Setter
    @Getter
    public static class DSProperties {
        private String beanName = "";
        private String tableName = "session";

    }
}
