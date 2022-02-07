package com.primestar.test.session;

import com.primestar.session.SyncSessionEventService;
import com.primestar.session.SyncSessionProperties;
import com.primestar.session.SyncSessionServiceImpl;
import org.springframework.lang.Nullable;

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
