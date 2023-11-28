package pers.clare.session;

public class SyncSessionId {
    String id;

    String username;

    public SyncSessionId() {

    }

    public SyncSessionId(String id, String username) {
        this.id = id;
        this.username = username;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }
}
