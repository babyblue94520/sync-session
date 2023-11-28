package pers.clare.session;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

@SuppressWarnings("unused")
public class SyncSession extends SyncSessionId implements Serializable {
    @JsonIgnore
    String id;

    @JsonIgnore
    long createTime;

    @JsonIgnore
    long maxInactiveInterval;

    @JsonIgnore
    long lastAccessTime;

    @JsonIgnore
    long effectiveTime;

    @JsonIgnore
    protected String username;

    @JsonIgnore
    volatile boolean valid = true;

    @JsonIgnore
    long lastUpdateAccessTime;

    String userAgent;

    String ip;

    public SyncSession() {
    }

    protected void save() {
        if (id != null) RequestCacheHolder.get().save();
    }

    public void invalidate() {
        RequestCacheHolder.get().invalidate(this);
    }

    void setLastAccessTime(long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
        this.effectiveTime = lastAccessTime + maxInactiveInterval;
    }

    public void setUsername(String username) {
        this.username = username;
        this.save();
    }

    public String getId() {
        return id;
    }

    public long getCreateTime() {
        return createTime;
    }

    public long getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public long getEffectiveTime() {
        return effectiveTime;
    }

    public String getUsername() {
        return username;
    }

    public boolean isValid() {
        return valid;
    }

    public long getLastUpdateAccessTime() {
        return lastUpdateAccessTime;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getIp() {
        return ip;
    }

}
