package pers.clare.test.controller;

import pers.clare.session.RequestCacheHolder;
import pers.clare.test.session.TokenSession;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("session")
public class SessionController {

    @PostMapping
    public String create(
            String username
    ) {
        TokenSession session = RequestCacheHolder.<TokenSession>get().getSession(true);
        session.setUsername(username);
        if (session.getCsrfToken() == null) {
            session.setCsrfToken(UUID.randomUUID().toString());
        }
        return session.getCsrfToken();
    }

    @GetMapping("token")
    public String token(
    ) {
        TokenSession session = RequestCacheHolder.<TokenSession>get().getSession(false);
        if (session == null) return null;
        return session.getCsrfToken();
    }

    @PostMapping("token/reset")
    public String resetToken( ) {
        TokenSession session = RequestCacheHolder.<TokenSession>get().getSession(false);
        if (session == null) return null;
        session.setCsrfToken(UUID.randomUUID().toString());
        return session.getCsrfToken();
    }

    @PostMapping("ping")
    public void ping(
    ) {
        RequestCacheHolder.get().setPing(true);
    }

    @DeleteMapping
    public void invalidate() {
        TokenSession session = RequestCacheHolder.<TokenSession>get().getSession(false);
        if (session == null) return;
        session.invalidate();
    }
}
