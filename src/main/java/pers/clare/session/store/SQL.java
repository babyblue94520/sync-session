package pers.clare.session.store;

import java.util.regex.Pattern;

public class SQL {
    private static final Pattern TableNamePattern = Pattern.compile("\\{tableName\\}");

    public final String find;
    public final String findAllByUsername;
    public final String deleteAllInvalidate;
    public final String insert;
    public final String update;
    public final String delete;
    public final String updateLastAccessTime;

    public SQL(String table) {
        find = "select create_time,max_inactive_interval,last_access_time,username,attributes from " + table + " where id = ?";
        findAllByUsername = "select id,username from " + table + " where username=?";

        insert = "insert into " + table + "(id,create_time,max_inactive_interval,last_access_time,username,attributes) values(?,?,?,?,?,?)";

        update = "update " + table + " set last_access_time=?,username=?,attributes=? where id=?";
        updateLastAccessTime = "update " + table + " set last_access_time=? where id=?";

        delete = "delete from " + table + " where id=?";
        deleteAllInvalidate = "delete from " + table + " where last_access_time < ? - max_inactive_interval";

    }

    public static String get(String sql, String tableName) {
        return TableNamePattern.matcher(sql).replaceAll(tableName);
    }

}
