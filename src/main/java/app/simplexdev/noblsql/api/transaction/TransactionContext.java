package app.simplexdev.noblsql.api.transaction;

import app.simplexdev.noblsql.api.sql.ResultSetMapper;

import java.sql.Savepoint;
import java.util.List;
import java.util.Map;

/**
 * Synchronous database operations bound to a single open transaction.
 * Instances are supplied by {@link app.simplexdev.noblsql.api.sql.SQLContract#transaction}
 * and must not be used outside the {@link TransactionWork} callback that received them.
 */
public interface TransactionContext {

    <T> T query(String sql, ResultSetMapper<T> mapper, Object... params) throws Exception;

    <T> List<T> queryMany(String sql, ResultSetMapper<T> mapper, Object... params) throws Exception;

    Map<String, Object> queryOneRaw(String sql, Object... params) throws Exception;

    List<Map<String, Object>> queryManyRaw(String sql, Object... params) throws Exception;

    int update(String sql, Object... params) throws Exception;

    long updateReturnKey(String sql, Object... params) throws Exception;

    int[] batch(String sql, List<Object[]> paramSets) throws Exception;

    Savepoint savepoint(String name) throws Exception;

    void rollbackTo(Savepoint savepoint) throws Exception;

    void releaseSavepoint(Savepoint savepoint) throws Exception;
}
