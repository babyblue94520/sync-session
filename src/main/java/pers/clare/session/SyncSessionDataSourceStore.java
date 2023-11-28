package pers.clare.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pers.clare.session.configuration.SyncSessionProperties;
import pers.clare.session.constant.SQL;
import pers.clare.session.exception.SyncSessionException;
import pers.clare.session.util.DataSourceSchemaUtil;
import pers.clare.session.util.JsonUtil;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SyncSessionDataSourceStore<T extends SyncSession> implements SyncSessionStore<T> {
    private static final Logger log = LogManager.getLogger();

    private final SQL sql;

    private final DataSource dataSource;
    private final SyncSessionProperties.DSProperties dsProperties;

    private final ObjectMapper om = JsonUtil.create();

    private final Class<T> sessionClass;

    public SyncSessionDataSourceStore(DataSource dataSource, SyncSessionProperties.DSProperties dsProperties, Class<T> sessionClass) {
        this.dataSource = dataSource;
        this.dsProperties = dsProperties;
        this.sessionClass = sessionClass;
        this.sql = new SQL(dsProperties.getTableName());
    }

    public void initSchema() {
        try {
            DataSourceSchemaUtil.init(dataSource, dsProperties.getTableName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public T newInstance() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return sessionClass.getConstructor().newInstance();
    }

    public T find(String id, Long time) {
        Long createTime = null, maxInactiveInterval = null, lastAccessTime = null, effectiveTime = null;
        String username = null, attributes = null;
        try (Connection conn = this.dataSource.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(sql.find);
            ps.setString(1, id);
            ps.setLong(2, time);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                createTime = rs.getLong(1);
                maxInactiveInterval = rs.getLong(2);
                lastAccessTime = rs.getLong(3);
                effectiveTime = rs.getLong(4);
                username = rs.getString(5);
                attributes = rs.getString(6);
            }
        } catch (SQLException e) {
            throw new SyncSessionException(e);
        }
        if (createTime == null) {
            return null;
        } else {
            T session = decodeAttributes(attributes);
            session.id = id;
            session.createTime = createTime;
            session.maxInactiveInterval = maxInactiveInterval;
            session.lastAccessTime = lastAccessTime;
            session.effectiveTime = effectiveTime;
            session.username = username;
            return session;
        }
    }

    public String findUsername(String id) {
        try (Connection conn = this.dataSource.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(sql.findUsername);
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString(1);
            }
            return null;
        } catch (SQLException e) {
            throw new SyncSessionException(e);
        }
    }

    @Override
    public List<SyncSessionId> findAll(String username, String... excludeSessionIds) {
        try (Connection conn = this.dataSource.getConnection()) {
            PreparedStatement ps;
            if (excludeSessionIds.length > 0) {
                StringBuilder sqlBuilder = new StringBuilder(sql.findAllId);
                sqlBuilder.append(" and id not in (?");
                for (int i = 1; i < excludeSessionIds.length; i++) {
                    sqlBuilder.append(',').append('?');
                }
                sqlBuilder.append(')');
                ps = conn.prepareStatement(sqlBuilder.toString());
                ps.setString(1, username);
                int index = 1;
                for (String excludeSessionId : excludeSessionIds) {
                    ps.setString(++index, excludeSessionId);
                }
            } else {
                ps = conn.prepareStatement(sql.findAllId);
                ps.setString(1, username);
            }
            ResultSet rs = ps.executeQuery();
            List<SyncSessionId> result = new ArrayList<>();
            while (rs.next()) {
                result.add(new SyncSessionId(rs.getString(1), username));
            }
            return result;
        } catch (SQLException e) {
            throw new SyncSessionException(e);
        }
    }

    public List<SyncSessionId> findAllInvalidate(Long time, Long count) {
        List<SyncSessionId> result = new ArrayList<>();
        if (time == null || count == null) return result;
        try (Connection conn = this.dataSource.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(sql.findAllInvalidate);
            ps.setLong(1, time);
            ps.setLong(2, count);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new SyncSessionId(rs.getString(1), rs.getString(2)));
            }
            return result;
        } catch (SQLException e) {
            throw new SyncSessionException(e);
        }
    }

    public void insert(T session) {
        String attributes = encodeAttributes(session);
        try (Connection conn = this.dataSource.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(sql.insert);
            ps.setString(1, session.id);
            ps.setLong(2, session.createTime);
            ps.setLong(3, session.maxInactiveInterval);
            ps.setLong(4, session.lastAccessTime);
            ps.setLong(5, session.effectiveTime);
            ps.setString(6, session.username);
            ps.setString(7, attributes);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new SyncSessionException(e);
        }
    }

    public int update(T session) {
        String attributes = encodeAttributes(session);
        try (Connection conn = this.dataSource.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(sql.update);
            ps.setLong(1, session.lastAccessTime);
            ps.setLong(2, session.effectiveTime);
            ps.setString(3, session.username);
            ps.setString(4, attributes);
            ps.setString(5, session.id);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new SyncSessionException(e);
        }
    }

    public int delete(String id) {
        try (Connection conn = this.dataSource.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(sql.delete);
            ps.setString(1, id);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new SyncSessionException(e);
        }
    }

    public int updateLastAccessTime(Collection<T> list) {
        int count = 0;
        try (Connection conn = this.dataSource.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(sql.updateLastAccessTime);
            for (T session : list) {
                try {
                    ps.setLong(1, session.lastAccessTime);
                    ps.setLong(2, session.effectiveTime);
                    ps.setString(3, session.id);
                    ps.setLong(4, session.effectiveTime);
                    count += ps.executeUpdate();
                } catch (Exception e) {
                    log.warn(e.getMessage(), e);
                }
            }
            return count;
        } catch (SQLException e) {
            throw new SyncSessionException(e);
        }
    }

    private String encodeAttributes(T session) {
        try {
            return om.writeValueAsString(session);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private T decodeAttributes(String json) {
        try {
            return om.readValue(json, sessionClass);
        } catch (IOException e) {
            throw new SyncSessionException(e);
        }
    }
}
