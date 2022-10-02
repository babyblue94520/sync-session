package pers.clare.session.exception;

import java.sql.SQLException;

public class SyncSessionException extends RuntimeException {
    private SQLException sqlException;

    public SyncSessionException(String message) {
        super(message);
    }

    public SyncSessionException(Throwable e) {
        super(e);
        if (e instanceof SQLException) {
            this.sqlException = (SQLException) e;
        }
    }

    public SQLException getSqlException() {
        return sqlException;
    }
}
