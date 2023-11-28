package pers.clare.session.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import pers.clare.session.SyncSession;
import pers.clare.session.constant.SessionMode;
import pers.clare.session.constant.StoreType;

import java.time.Duration;


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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SessionMode getMode() {
        return mode;
    }

    public void setMode(SessionMode mode) {
        this.mode = mode;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Long getBatchInvalidateCount() {
        return batchInvalidateCount;
    }

    public void setBatchInvalidateCount(Long batchInvalidateCount) {
        this.batchInvalidateCount = batchInvalidateCount;
    }

    public LocalProperties getLocal() {
        return local;
    }

    public void setLocal(LocalProperties local) {
        this.local = local;
    }

    public StoreType getStore() {
        return store;
    }

    public void setStore(StoreType store) {
        this.store = store;
    }

    public DSProperties getDs() {
        return ds;
    }

    public void setDs(DSProperties ds) {
        this.ds = ds;
    }

    public Class<? extends SyncSession> getClazz() {
        return clazz;
    }

    public void setClazz(Class<? extends SyncSession> clazz) {
        this.clazz = clazz;
    }

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

        public boolean isPersistence() {
            return persistence;
        }

        public void setPersistence(boolean persistence) {
            this.persistence = persistence;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }
    }

    public static class DSProperties {
        private String beanName = "";
        private String tableName = "session";

        public String getBeanName() {
            return beanName;
        }

        public void setBeanName(String beanName) {
            this.beanName = beanName;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }
    }
}
