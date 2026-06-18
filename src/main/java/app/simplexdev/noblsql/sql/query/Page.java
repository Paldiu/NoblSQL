package app.simplexdev.noblsql.sql.query;

import java.util.List;

/**
 * A single page of query results together with the total row count across all pages.
 * Returned by {@link QueryBuilder#paginate(int, int)}.
 *
 * @param items      rows on this page (may be fewer than {@code size} on the last page)
 * @param totalCount total rows matching the query across all pages
 * @param page       zero-based page index that was requested
 * @param size       maximum rows per page that was requested
 */
public record Page<T>(List<T> items, long totalCount, int page, int size) {

    /** Total number of pages given the current {@link #size}. */
    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalCount / size);
    }

    /** {@code true} if there is at least one more page after this one. */
    public boolean hasNext() {
        return (long) page * size + items.size() < totalCount;
    }

    /** {@code true} if this is not the first page. */
    public boolean hasPrevious() {
        return page > 0;
    }
}
