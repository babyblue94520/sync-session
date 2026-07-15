package pers.clare.session.store;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import pers.clare.session.configuration.SyncSessionProperties;
import pers.clare.test.session.TokenSession;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SyncSessionStateFlagsTest {

    @Test
    void flagsAreFalseWhenSessionIsLoadedFromDatabase() throws Exception {
        long now = System.currentTimeMillis();

        SyncSessionDataSourceStore<TokenSession> store = new SyncSessionDataSourceStore<>();
        store.setProperties(dataSourceProperties());

        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("dataSource", new DriverManagerDataSource(
                "jdbc:h2:mem:sync-session-flags;MODE=MYSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE",
                "sa",
                ""
        ));
        store.setBeanFactory(beanFactory);
        store.afterPropertiesSet();

        TokenSession session = new TokenSession();
        session.setId("db-session");
        session.setCreateTime(now);
        session.setMaxInactiveInterval(60_000L);
        session.setLastAccessTime(now);
        session.setUsername("clare");
        session.setCsrfToken("token");
        session.setAsNew();

        store.insert(session);

        TokenSession loaded = store.find(session.getId(), now);

        assertNotNull(loaded);
        assertFalse(loaded.isNew());
        assertFalse(loaded.isChanged());
        assertEquals(loaded.getLastAccessTime(), loaded.getLastUpdateAccessTime());
    }

    @Test
    void flagsAreFalseWhenSessionIsDeserialized() throws Exception {
        TokenSession session = new TokenSession();
        session.setId("serialized-session");
        session.setUsername("clare");
        session.setCsrfToken("token");
        session.setAsNew();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(output)) {
            objectOutputStream.writeObject(session);
        }

        TokenSession restored;
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(output.toByteArray()))) {
            restored = (TokenSession) objectInputStream.readObject();
        }

        assertFalse(restored.isNew());
        assertFalse(restored.isChanged());
    }

    private SyncSessionProperties dataSourceProperties() {
        SyncSessionProperties properties = new SyncSessionProperties();
        properties.setClazz(TokenSession.class);
        properties.getDs().setTableName("test_session_flags");
        return properties;
    }
}
