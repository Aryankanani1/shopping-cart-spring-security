package com.aryan.spring_security_demo.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable, transport-facing pagination envelope.
 *
 * <p>We never serialize Spring Data's {@code Page}/{@code PageImpl} directly: its
 * JSON shape is an implementation detail (Spring Boot 3.3+ even logs a warning
 * about it) and it would drag the entity type into the wire contract. Instead the
 * service maps {@code Page<Entity>} to {@code Page<Dto>} and the controller wraps
 * it here, so the API exposes only DTOs and a fixed field set.
 *
 * @param content         the DTOs on this page
 * @param number          current page index (zero-based)
 * @param size            requested page size
 * @param totalElements   total matching rows across all pages
 * @param totalPages      total number of pages
 * @param first           whether this is the first page
 * @param last            whether this is the last page
 * @param numberOfElements items actually on THIS page
 */
@JsonPropertyOrder({"content", "number", "size", "totalElements", "totalPages", "first", "last", "numberOfElements"})
public record PagedResponse<T>(
        List<T> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        int numberOfElements
) {
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.getNumberOfElements()
        );
    }
}
