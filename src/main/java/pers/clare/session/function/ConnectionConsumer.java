package pers.clare.session.function;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface ConnectionConsumer<T> {
   T accept(Connection connection) throws SQLException;
}
