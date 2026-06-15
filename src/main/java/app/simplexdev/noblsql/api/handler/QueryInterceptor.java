package app.simplexdev.noblsql.api.handler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link QueryHandler} implementation with routing metadata.
 *
 * <p>Priority is read by {@code HandlerChain} when ordering handlers (higher = earlier).
 * {@code intercepts} controls which {@link QueryType}s this handler is invoked for;
 * omit to receive all types.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface QueryInterceptor {
    int priority() default 0;
    QueryType[] intercepts() default { QueryType.SELECT, QueryType.UPDATE, QueryType.DDL };
}
