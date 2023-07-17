package pers.clare.test.session;

import org.springframework.lang.Nullable;
import pers.clare.session.SyncSessionEventService;
import pers.clare.session.SyncSessionProperties;
import pers.clare.session.SyncSessionServiceImpl;

import javax.sql.DataSource;

public class TokenSessionService extends SyncSessionServiceImpl<TokenSession> {
    public TokenSessionService(
            SyncSessionProperties properties
            , DataSource dataSource
            , @Nullable SyncSessionEventService sessionEventService
    ) {
        super(properties, dataSource, sessionEventService);
    }
}
