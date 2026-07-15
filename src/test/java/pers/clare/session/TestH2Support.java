package pers.clare.session;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import pers.clare.h2.H2Application;

import javax.sql.DataSource;

final class TestH2Support {

    private TestH2Support() {
    }

    static ConfigurableApplicationContext start(int port, String mode, String dbName) {
        ConfigurableApplicationContext context = SpringApplication.run(H2Application.class,
                "--spring.profiles.active=h2server",
                "--spring.main.web-application-type=none",
                "--h2.port=" + port,
                "--h2.mode=" + mode,
                "--h2.dbName=" + dbName
        );
        initializeDatabase(context);
        return context;
    }

    private static void initializeDatabase(ConfigurableApplicationContext context) {
        try (var connection = context.getBean(DataSource.class).getConnection()) {
            connection.getMetaData();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize H2 test database.", e);
        }
    }
}
