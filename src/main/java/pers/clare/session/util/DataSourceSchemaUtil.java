package pers.clare.session.util;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

public class DataSourceSchemaUtil {

    public static void init(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            Statement statement = connection.createStatement();
            String schema = getInitSchema(databaseMetaData.getDatabaseProductName());
            if (schema == null) return;
            String[] commands = schema.split(";");
            for (String command : commands) {
                statement.executeUpdate(command);
            }
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        }
    }

    private static String getInitSchema(String database) {
        try (InputStream inputStream = ClassLoader.getSystemResourceAsStream("schema/"+database + ".sql")) {
            if (inputStream != null) {
                return new BufferedReader(new InputStreamReader(inputStream))
                        .lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
