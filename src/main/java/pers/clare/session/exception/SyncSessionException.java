package pers.clare.session.exception;

import lombok.Getter;

import java.sql.SQLException;

public class SyncSessionException extends RuntimeException {
    @Getter
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
}
