package pers.clare.session;

import org.springframework.context.ConfigurableApplicationContext;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

@EnabledIfSystemProperty(named = SessionCreateStressTest.ENABLED_PROPERTY, matches = "true")
class SessionCreateStressH2Test extends SessionCreateStressTest {

    private ConfigurableApplicationContext h2Context;
    private int h2Port;

    @Override
    protected String profileName() {
        return "h2";
    }

    @Override
    protected void configureDatabase(List<String> args, String dbName) {
        h2Port = TestPorts.freePort();
        h2Context = TestH2Support.start(h2Port, "MYSQL", dbName);
        args.add("--spring.datasource.url=jdbc:h2:tcp://localhost:" + h2Port + "/mem:" + dbName + ";MODE=MYSQL;DATABASE_TO_UPPER=FALSE");
    }

    @Override
    protected void closeDatabase() {
        if (h2Context != null) {
            h2Context.close();
        }
    }
}
