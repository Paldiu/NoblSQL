package app.simplexdev.noblsql.util;

import java.util.Collections;

public final class SQLUtils {
    private SQLUtils() {}

    public static String placeholders(final int count) {
        if (count <= 0) return "";
        return String.join(", ", Collections.nCopies(count, "?"));
    }

}
