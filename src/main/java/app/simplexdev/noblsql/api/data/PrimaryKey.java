package app.simplexdev.noblsql.api.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code @Column}-annotated field as (part of) the table's primary key.
 * Multiple {@code @PrimaryKey} fields on the same entity form a composite key.
 *
 * <p>Interchangeable with {@link Id} — both are recognised by {@code EntityMapper}
 * and {@code SchemaGenerator}. {@code @PrimaryKey} is preferred for new code as the
 * name is more explicit.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface PrimaryKey {
}
