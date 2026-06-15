package app.simplexdev.noblsql.sql.transaction;

import app.simplexdev.noblsql.api.sql.ResultSetMapper;
import app.simplexdev.noblsql.api.transaction.TransactionContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class JdbcTransactionContext implements TransactionContext {

    private final Connection conn;

    public JdbcTransactionContext(final Connection conn) {
        this.conn = conn;
    }

    @Override
    public <T> T query(final String sql, final ResultSetMapper<T> mapper, final Object... params) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapper.map(rs) : null;
            }
        }
    }

    @Override
    public <T> List<T> queryMany(final String sql, final ResultSetMapper<T> mapper, final Object... params) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                final List<T> rows = new ArrayList<>();
                while (rs.next()) rows.add(mapper.map(rs));
                return rows;
            }
        }
    }

    @Override
    public Map<String, Object> queryOneRaw(final String sql, final Object... params) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? snapRow(rs) : null;
            }
        }
    }

    @Override
    public List<Map<String, Object>> queryManyRaw(final String sql, final Object... params) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                final List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) rows.add(snapRow(rs));
                return rows;
            }
        }
    }

    @Override
    public int update(final String sql, final Object... params) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindParams(stmt, params);
            return stmt.executeUpdate();
        }
    }

    @Override
    public long updateReturnKey(final String sql, final Object... params) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindParams(stmt, params);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    @Override
    public int[] batch(final String sql, final List<Object[]> paramSets) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (final Object[] params : paramSets) {
                bindParams(stmt, params);
                stmt.addBatch();
            }
            return stmt.executeBatch();
        }
    }

    @Override
    public Savepoint savepoint(final String name) throws Exception {
        return conn.setSavepoint(name);
    }

    @Override
    public void rollbackTo(final Savepoint savepoint) throws Exception {
        conn.rollback(savepoint);
    }

    @Override
    public void releaseSavepoint(final Savepoint savepoint) throws Exception {
        conn.releaseSavepoint(savepoint);
    }

    private static void bindParams(final PreparedStatement stmt, final Object[] params) throws Exception {
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
    }

    private static Map<String, Object> snapRow(final ResultSet rs) throws Exception {
        final ResultSetMetaData meta = rs.getMetaData();
        final Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            row.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT), rs.getObject(i));
        }
        return row;
    }
}
