package pers.clare.session.constant;

public enum SessionMode {
    /** Session ID is stored in cookie.*/
    Cookie
    /** Get and set session ID via header.*/
    , Header
    /** Get from response header and set session ID via queryString */
    , QueryString
}
