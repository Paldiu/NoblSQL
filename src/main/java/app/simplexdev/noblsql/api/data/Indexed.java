package app.simplexdev.noblsql.api.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Indexed {
    /** Explicit index name. Defaults to {@code idx_<table>_<column>} if blank. */
    String name() default "";
}
