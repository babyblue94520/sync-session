package pers.clare.session.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.util.StringUtils;
import pers.clare.session.configuration.SyncSessionProperties;
import pers.clare.session.exception.SyncSessionException;
import pers.clare.session.function.ConnectionConsumer;
import pers.clare.session.service.SyncSession;
import pers.clare.session.service.SyncSessionId;
import pers.clare.session.util.DataSourceSchemaUtil;
import pers.clare.session.util.JsonUtil;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
public class SyncSessionDataSourceStore<T extends SyncSession> implements SyncSessionStore<T>, InitializingBean {

    private final ObjectMapper om = JsonUtil.create();

    @Setter(onMethod_ = {@Autowired})
    private DefaultListableBeanFactory beanFactory;

    private DataSource dataSource;

    @Setter(onMethod_ = {@Autowired})
    private SyncSessionProperties properties;

    private Class<T> sessionClass;

    private SQL sql;

    @Override
    public void afterPropertiesSet() {
        this.sessionClass = (Class<T>) properties.getClazz();

        String beanName = properties.getDs().getBeanName();
        if (StringUtils.hasLength(beanName)) {
            dataSource = beanFactory.getBean(beanName, DataSource.class);
        } else {
            dataSource = beanFactory.getBean(DataSource.class);
        }

        this.sql = new SQL(properties.getDs().getTableName());
        initSchema();
    }

    protected void initSchema() {
        try {
            DataSourceSchemaUtil.init(dataSource, properties.getDs().getTableName());
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
    }

    @Override
    public T newInstance() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return sessionClass.getConstructor().newInstance();
    }

    public T find(String id, Long time) {
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(sql.find);
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long maxInactiveInterval = rs.getLong(2);
                long lastAccessTime = rs.getLong(3);
                if (lastAccessTime + maxInactiveInterval > time) {
                    T session = decodeAttributes(rs.getString(5));
                    session.setId(id);
                    session.setCreateTime(rs.getLong(1));
                    session.setMaxInactiveInterval(maxInactiveInterval);
                    session.setLastAccessTime(lastAccessTime);
                    session.setLastUpdateAccessTime(lastAccessTime);
                    session.setUsername(rs.getString(4));
                    session.setAsUpdated();
                    return session;
                }
            }
            return null;
        } catch (SQLException e) {
            throw new SyncSessionException(e);
        }
    }

    public void insert(T session) {
        String attributes = encodeAttributes(session);
        commit(conn -> {
            PreparedStatement ps = conn.prepareStatement(sql.insert);
            ps.setString(1, session.getId());
            ps.setLong(2, session.getCreateTime());
            ps.setLong(3, session.getMaxInactiveInterval());
            ps.setLong(4, session.getLastAccessTime());
            ps.setString(5, session.getUsername());
            ps.setString(6, attributes);
            return ps.executeUpdate();
        });
    }

    public int update(T session) {
        String attributes = encodeAttributes(session);
        return commit(conn -> {
            PreparedStatement ps = conn.prepareStatement(sql.update);
            ps.setLong(1, session.getLastAccessTime());
            ps.setString(2, session.getUsername());
            ps.setString(3, attributes);
            ps.setString(4, session.getId());
            return ps.executeUpdate();
        });
    }

    @Override
    public Collection<SyncSessionId> findAllByUsername(String username) {
        if (!StringUtils.hasLength(username)) return List.of();
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(sql.findAllByUsername);
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            List<SyncSessionId> ids = new ArrayList<>();
            while (rs.next()) {
                ids.add(new SyncSessionId(rs.getString(1), rs.getString(2)));
            }
            return ids;
        } catch (SQLException e) {
            throw new SyncSessionException(e);
        }
    }

    public int delete(String id) {
        return commit(conn -> {
            PreparedStatement ps = conn.prepareStatement(sql.delete);
            ps.setString(1, id);
            return ps.executeUpdate();
        });
    }

    public int[] updateLastAccessTime(List<T> list) {
        int[] counts = new int[list.size()];
        Integer configBatchSize = properties.getDs().getUpdateBatchSize();
        int batchSize = configBatchSize == null || configBatchSize < 1 ? 1000 : configBatchSize;
        for (int i = 0; i < list.size(); i += batchSize) {
            var subList = list.subList(i, Math.min(i + batchSize, list.size()));
            int[] batchCounts = doUpdateLastAccessTime(subList);
            System.arraycopy(batchCounts, 0, counts, i, batchCounts.length);
        }
        return counts;
    }

    private int[] doUpdateLastAccessTime(List<T> list) {
        return transaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql.updateLastAccessTime)) {
                for (T session : list) {
                    ps.setLong(1, session.getLastAccessTime());
                    ps.setString(2, session.getId());
                    ps.addBatch();
                }
                int[] counts = ps.executeBatch();
                int index = 0;
                for (int result : counts) {
                    T session = list.get(index++);
                    if (result == Statement.EXECUTE_FAILED) {
                        throw new SQLException("Batch update session lastAccessTime failed.");
                    }
                    if (result > 0 || result == Statement.SUCCESS_NO_INFO) {
                        session.setLastUpdateAccessTime(session.getLastAccessTime());
                    }
                }
                return counts;
            }
        });
    }

    @Override
    public int deleteAllInvalidate(Long time) {
        if (time == null) return 0;
        return commit(conn -> {
            PreparedStatement ps = conn.prepareStatement(sql.deleteAllInvalidate);
            ps.setLong(1, time);
            return ps.executeUpdate();
        });
    }

    private <R> R commit(ConnectionConsumer<R> consumer) {
        boolean originValue = true;
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            originValue = conn.getAutoCommit();
            conn.setAutoCommit(true);
            return consumer.accept(conn);
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    log.error(e.getMessage(), ex);
                }
            }
            throw new SyncSessionException(e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(originValue);
                    conn.close();
                } catch (SQLException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }

    private <R> R transaction(ConnectionConsumer<R> consumer) {
        boolean originValue = true;
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            originValue = conn.getAutoCommit();
            conn.setAutoCommit(false);
            R result = consumer.accept(conn);
            conn.commit();
            return result;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    log.error(e.getMessage(), ex);
                }
            }
            throw new SyncSessionException(e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(originValue);
                    conn.close();
                } catch (SQLException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }

    private String encodeAttributes(T session) {
        try {
            return om.writeValueAsString(session);
        } catch (JsonProcessingException e) {
            throw new SyncSessionException(e);
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
