package pers.clare.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;


@ConfigurationProperties(prefix = SyncSessionProperties.PREFIX)
public class SyncSessionProperties {
    public static final String PREFIX = "sync-session";

    /**
     * Cookie name
     */
    private String cookieName = "SSESSIONID";

    /**
     * Session timeout
     */
    private Duration timeout = Duration.ofMinutes(30);

    private Long batchInvalidateCount = 100L;

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
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
}
