package com.aryan.spring_security_demo.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * Cursor-paginated (keyset) slice envelope, for endpoints that scroll forward
 * without random page access.
 *
 * <p>Unlike {@link PagedResponse}, this intentionally carries <b>no</b>
 * {@code totalElements}/{@code totalPages}: computing them needs a {@code COUNT(*)}
 * on every request, and a "load more" feed never renders a total. Instead the
 * client sends {@link #nextCursor} back to fetch the next slice — cost is flat
 * regardless of how deep the scroll goes, and the slice is stable under concurrent
 * inserts (the cursor anchors to data, not to an offset).
 *
 * @param content          the DTOs on this slice
 * @param size             requested slice size
 * @param numberOfElements items actually on THIS slice
 * @param hasNext          whether another slice exists after this one
 * @param nextCursor       opaque cursor to fetch the next slice ({@code null} when last)
 */
@JsonPropertyOrder({"content", "size", "numberOfElements", "hasNext", "nextCursor"})
public record SlicedResponse<T>(
        List<T> content,
        int size,
        int numberOfElements,
        boolean hasNext,
        String nextCursor
) {
}
