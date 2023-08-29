package pers.clare.session.constant;

import java.util.regex.Pattern;

public class SQL {
    private static Pattern TableNamePattern = Pattern.compile("\\{tableName\\}");


    public final String find;
    public final String findUsername;
    public final String findAllInvalidate;
    public final String findAllId;
    public final String insert;
    public final String update;
    public final String delete;
    public final String updateLastAccessTime;

    public SQL(String tableName) {
        String find1 = "select create_time,max_inactive_interval,last_access_time,effective_time,username,attributes from `{tableName}` where id = ? and effective_time >= ?";
        find = SQL.get(find1, tableName);
        String findUsername1 = "select username from `{tableName}` where id = ? ";
        findUsername = SQL.get(findUsername1, tableName);
        String findAllInvalidate1 = "select id,username from `{tableName}` where effective_time<? limit ?";
        findAllInvalidate = SQL.get(findAllInvalidate1, tableName);
        String findAllId1 = "select id from `{tableName}` where username=?";
        findAllId = SQL.get(findAllId1, tableName);
        String insert1 = "insert into `{tableName}`(id,create_time,max_inactive_interval,last_access_time,effective_time,username,attributes) values(?,?,?,?,?,?,?)";
        insert = SQL.get(insert1, tableName);
        String update1 = "update `{tableName}` set last_access_time=?,effective_time=?,username=?,attributes=? where id=?";
        update = SQL.get(update1, tableName);
        String delete1 = "delete from `{tableName}` where id=?";
        delete = SQL.get(delete1, tableName);
        String updateLastAccessTime1 = "update `{tableName}` set last_access_time=?,effective_time=? where id=? and effective_time<?";
        updateLastAccessTime = SQL.get(updateLastAccessTime1, tableName);
    }

    public static String get(String sql, String tableName) {
        return TableNamePattern.matcher(sql).replaceAll(tableName);
    }

}
