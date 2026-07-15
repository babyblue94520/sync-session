package pers.clare.session.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

@Getter
public class SyncSessionId implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @JsonIgnore
    @Setter
    String id;

    @JsonIgnore
    String username;

    public SyncSessionId() {
    }

    public SyncSessionId(String id, String username) {
        this.id = id;
        this.username = username;
    }

}
