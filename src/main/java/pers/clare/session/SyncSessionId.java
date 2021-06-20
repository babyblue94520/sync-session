package pers.clare.session;

public class SyncSessionId {
    private final String id;

    private final String username;

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
