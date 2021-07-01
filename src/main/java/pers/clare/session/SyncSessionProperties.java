package pers.clare.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;


@ConfigurationProperties(prefix = SyncSessionProperties.PREFIX)
public class SyncSessionProperties {
    public static final String PREFIX = "sync-session";

    private String cookieName = "SSESSIONID";

    private Duration timeout = Duration.ofMinutes(30);

    private String topic = "sync.session";

    private int checkTimeoutWorkers = 1;

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

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getCheckTimeoutWorkers() {
        return checkTimeoutWorkers;
    }

    public void setCheckTimeoutWorkers(int checkTimeoutWorkers) {
        this.checkTimeoutWorkers = checkTimeoutWorkers;
    }
}
