package pers.clare.session;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = SessionCreateStressTest.ENABLED_PROPERTY, matches = "true")
class SessionCreateStressMysqlTest extends SessionCreateStressTest {

    @Override
    protected String profileName() {
        return "mysql";
    }
}
