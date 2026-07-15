package pers.clare.test2;

import org.springframework.web.bind.annotation.*;
import pers.clare.session.SyncSessionRequestContextHolder;
import pers.clare.test.session.TokenSession;

import java.util.UUID;

@RestController
@RequestMapping("session")
public class SessionController {

    @PostMapping
    public String create(
            String username,
            Boolean includeSessionId
    ) {
        TokenSession session = SyncSessionRequestContextHolder.<TokenSession>get().getSession(true);
        session.setUsername(username);
        if (session.getCsrfToken() == null) {
            session.setCsrfToken(UUID.randomUUID().toString());
        }
        if (Boolean.TRUE.equals(includeSessionId)) {
            return session.getId() + ":" + session.getCsrfToken();
        }
        return session.getCsrfToken();
    }

    @GetMapping("token")
    public String token(
    ) {
        TokenSession session = SyncSessionRequestContextHolder.<TokenSession>get().getSession(false);
        if (session == null) return null;
        return session.getCsrfToken();
    }

    @PostMapping("token/reset")
    public String resetToken( ) {
        TokenSession session = SyncSessionRequestContextHolder.<TokenSession>get().getSession(false);
        if (session == null) return null;
        session.setCsrfToken(UUID.randomUUID().toString());
        return session.getCsrfToken();
    }

    @PostMapping("ping")
    public void ping(
    ) {
        SyncSessionRequestContextHolder.get().setPing(true);
    }

    @DeleteMapping
    public void invalidate() {
        TokenSession session = SyncSessionRequestContextHolder.<TokenSession>get().getSession(false);
        if (session == null) return;
        session.invalidate();
    }
}
