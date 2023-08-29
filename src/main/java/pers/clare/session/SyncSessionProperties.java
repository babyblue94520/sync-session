package pers.clare.session;

import org.springframework.boot.context.properties.ConfigurationProperties;
import pers.clare.session.constant.SessionMode;

import java.time.Duration;


@ConfigurationProperties(prefix = SyncSessionProperties.PREFIX)
public class SyncSessionProperties {
    public static final String PREFIX = "sync-session";

    /**
     * Cookie name
     */
    private String name = "SSESSIONID";

    private SessionMode mode = SessionMode.Cookie;

    /**
     * Session timeout
     */
    private Duration timeout = Duration.ofMinutes(30);

    private Long batchInvalidateCount = 100L;

    private String tableName = "session";

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

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
}
