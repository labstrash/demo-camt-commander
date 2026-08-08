package com.example.commander.adapter.persistence;

import java.util.List;
import java.util.Optional;

/** Shared helpers for the JDBC repository implementations in this package. */
public final class JdbcRepositorySupport {

    private JdbcRepositorySupport() {}

    /**
     * Returns a single row from a list, or empty if the list is empty.
     *
     * @param rows the result list
     * @param context appended to the exception message if more than one row is found —
     *     explains why the caller expected uniqueness (e.g. which constraint backs it, or which
     *     assumption might not hold for the underlying data)
     * @param <T> the row type
     * @return the single row if present, or empty
     * @throws IllegalStateException if more than one row is found
     */
    public static <T> Optional<T> singleRow(List<T> rows, String context) {
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() > 1) {
            throw new IllegalStateException("Expected at most one row but found " + rows.size() + " — " + context);
        }
        return Optional.of(rows.get(0));
    }
}
