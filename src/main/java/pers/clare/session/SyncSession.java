package pers.clare.session;

import com.fasterxml.jackson.annotation.JsonIgnore;
import pers.clare.session.support.VolatileBlock;

@SuppressWarnings("unused")
public class SyncSession {
    @JsonIgnore
    String id;

    @JsonIgnore
    long createTime;

    @JsonIgnore
    long maxInactiveInterval;

    @JsonIgnore
    long lastAccessTime;

    @JsonIgnore
    protected String username;

    @JsonIgnore
    volatile boolean valid = true;

    @JsonIgnore
    long lastUpdateAccessTime;

    String userAgent;

    String ip;

    @JsonIgnore
    VolatileBlock refresh = new VolatileBlock(5000);

    protected void save() {
        RequestCacheHolder.get().save();
    }

    public void invalidate() {
        RequestCacheHolder.get().invalidate();
    }

    void setLastAccessTime(long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
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

    public VolatileBlock getRefresh() {
        return refresh;
    }
}
