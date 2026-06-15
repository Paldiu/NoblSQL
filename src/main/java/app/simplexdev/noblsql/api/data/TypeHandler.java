package app.simplexdev.noblsql.api.data;

/**
 * Converts between a Java field value and the SQL-bindable representation.
 * Implement this to store non-primitive types (e.g. serialized objects, enums by name,
 * JSON blobs) without exposing the serialization logic to callers.
 */
public interface TypeHandler<T> {
    /** Converts a Java value to the object that will be bound via {@code PreparedStatement.setObject}. */
    Object toSql(T value);

    /** Converts the raw SQL value (as returned by {@code ResultSet.getObject}) back to the Java type. */
    T fromSql(Object value);
}
