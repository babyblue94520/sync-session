package com.primestar.test.session;

import com.primestar.session.SyncSession;

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
