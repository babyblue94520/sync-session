package pers.clare.session.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;


@Getter
@SuppressWarnings("unused")
public class SyncSession extends SyncSessionId implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @JsonIgnore
    transient SyncSessionService service;

    @Getter(onMethod_ = @JsonIgnore)
    transient boolean isNew = false;

    @Getter(onMethod_ = @JsonIgnore)
    transient boolean changed = false;

    @Getter(onMethod_ = @JsonIgnore)
    transient volatile boolean valid = true;

    @Getter(onMethod_ = @JsonIgnore)
    @Setter
    long createTime;

    @Getter(onMethod_ = @JsonIgnore)
    @Setter
    long maxInactiveInterval;

    @Getter(onMethod_ = @JsonIgnore)
    @Setter
    long lastAccessTime;

    @Getter(onMethod_ = @JsonIgnore)
    @Setter
    long lastUpdateAccessTime;

    @Getter
    String userAgent;

    @Getter
    String ip;

    public void invalidate() {
        if (service != null) {
            service.invalidate(this);
        }
    }

    public void setAsNew() {
        this.isNew = true;
    }

    public void setAsChanged() {
        this.changed = true;
    }

    public void setAsUpdated() {
        this.isNew = false;
        this.changed = false;
    }

    public void setUsername(String username) {
        this.username = username;
        this.setAsChanged();
    }
}
