package pers.clare.session.constant;

public class InvalidateBy {
    /**
     * The session is invalidated by the current service
     */
    public static final int SELF = 1;
    /**
     * The session is invalidated by the another service
     */
    public static final int NOTICE = 2;

    private InvalidateBy() {
    }
}
