package pers.clare.session;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = SessionCreateStressTest.ENABLED_PROPERTY, matches = "true")
class SessionCreateStressPostgresTest extends SessionCreateStressTest {

    @Override
    protected String profileName() {
        return "postgres";
    }
}
