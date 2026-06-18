package app.simplexdev.noblsql.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class SQLUtils {
    private SQLUtils() {}

    public static String placeholders(final int count) {
        if (count <= 0) return "";
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    /**
     * Splits {@code values} into consecutive sublists of at most {@code chunkSize} elements.
     * Use this before building {@code IN (?, …)} clauses to stay within database
     * bind-parameter limits (e.g. SQLite caps at 999, SQL Server at 2100).
     *
     * <pre>{@code
     * for (List<Long> chunk : SQLUtils.chunkForIn(ids, 500)) {
     *     contract.queryMany("SELECT * FROM t WHERE id IN (" + placeholders(chunk.size()) + ")",
     *                        rs -> …, chunk.toArray());
     * }
     * }</pre>
     */
    public static <T> List<List<T>> chunkForIn(final Collection<T> values, final int chunkSize) {
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be > 0");
        if (values.isEmpty()) return List.of();
        final List<T> flat = new ArrayList<>(values);
        final List<List<T>> chunks = new ArrayList<>((flat.size() + chunkSize - 1) / chunkSize);
        for (int i = 0; i < flat.size(); i += chunkSize) {
            chunks.add(List.copyOf(flat.subList(i, Math.min(i + chunkSize, flat.size()))));
        }
        return List.copyOf(chunks);
    }
}
