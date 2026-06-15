package app.simplexdev.noblsql.api.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attaches a {@link TypeHandler} to a field. The handler is instantiated once
 * (via no-arg constructor) and cached by {@code EntityMapper}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Handles {
    Class<? extends TypeHandler<?>> value();
}
