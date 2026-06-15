package app.simplexdev.noblsql.api.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ForeignKey {
    String table();
    String column();
    Action onDelete() default Action.RESTRICT;
    Action onUpdate() default Action.RESTRICT;

    enum Action {
        CASCADE("CASCADE"),
        SET_NULL("SET NULL"),
        RESTRICT("RESTRICT"),
        NO_ACTION("NO ACTION");

        private final String sql;

        Action(final String sql) {
            this.sql = sql;
        }

        public String sql() {
            return sql;
        }
    }
}
