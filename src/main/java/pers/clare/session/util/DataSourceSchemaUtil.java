package pers.clare.session.util;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.lang.NonNull;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

public class DataSourceSchemaUtil {

    public static void init(@NonNull DataSource dataSource) throws SQLException {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            populator.addScript(new ClassPathResource("schema/" + databaseMetaData.getDatabaseProductName() + ".sql"));
            populator.setContinueOnError(true);
            populator.populate(connection);
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        }
    }
}
