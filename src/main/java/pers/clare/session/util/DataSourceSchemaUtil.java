package pers.clare.session.util;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.lang.NonNull;
import pers.clare.session.constant.SQL;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.regex.Pattern;

public class DataSourceSchemaUtil {

    public static void init(@NonNull DataSource dataSource, String tableName) throws SQLException, IOException {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        ClassPathResource resource = null;
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            resource = new ClassPathResource("schema/" + databaseMetaData.getDatabaseProductName() + ".sql");

            String sql = new String(resource.getInputStream().readAllBytes());
            sql = SQL.get(sql, tableName);

            populator.addScript(new ByteArrayResource(sql.getBytes()));
            populator.setContinueOnError(true);
            populator.populate(connection);
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        } finally {
            if (resource != null) {
                resource.getInputStream().close();
            }
        }
    }
}
