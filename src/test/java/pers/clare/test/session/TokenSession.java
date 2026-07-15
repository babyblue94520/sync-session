package pers.clare.test.session;

import lombok.Getter;
import pers.clare.session.service.SyncSession;

@Getter
public class TokenSession extends SyncSession {
    private String csrfToken;

    public void setCsrfToken(String csrfToken) {
        this.csrfToken = csrfToken;
        this.setAsChanged();
    }
}
