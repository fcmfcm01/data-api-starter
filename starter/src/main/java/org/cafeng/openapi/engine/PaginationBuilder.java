package org.cafeng.openapi.engine;

/**
 * Appends dialect-appropriate pagination clauses to a query.
 *
 * <p>For {@link SqlDialect#MSSQL} generates {@code OFFSET N ROWS FETCH NEXT M ROWS ONLY}.
 * For all other dialects generates {@code LIMIT M OFFSET N}.
 * Safety limits: page size capped at 1000, page number capped at 10000.
 * Uses {@code Math.multiplyExact} to catch integer overflow on deep offsets.</p>
 */
public class PaginationBuilder {

    private static final int MAX_PAGE_SIZE = 1000;
    private static final int MAX_PAGE = 10000;

    public String build(String baseSql, int page, int size) {
        return build(baseSql, page, size, SqlDialect.MSSQL);
    }

    public String build(String baseSql, int page, int size, SqlDialect dialect) {
        if (page <= 0 || size <= 0) {
            throw new IllegalArgumentException("Page and size must be positive");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size cannot exceed " + MAX_PAGE_SIZE);
        }
        if (page > MAX_PAGE) {
            throw new IllegalArgumentException("Page number cannot exceed " + MAX_PAGE);
        }
        
        int offset = Math.multiplyExact(Math.subtractExact(page, 1), size);
        if (dialect == SqlDialect.MSSQL) {
            return baseSql + " OFFSET " + offset + " ROWS FETCH NEXT " + size + " ROWS ONLY";
        } else {
            return baseSql + " LIMIT " + size + " OFFSET " + offset;
        }
    }
}