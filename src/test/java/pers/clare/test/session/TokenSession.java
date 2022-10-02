package pers.clare.test.session;

import pers.clare.session.SyncSession;

public class TokenSession extends SyncSession {
    private String csrfToken;

    public String getCsrfToken() {
        return csrfToken;
    }

    public void setCsrfToken(String csrfToken) {
        this.csrfToken = csrfToken;
        this.save();
    }
}
