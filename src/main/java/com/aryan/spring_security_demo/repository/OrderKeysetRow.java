package com.aryan.spring_security_demo.repository;

import java.time.Instant;

/**
 * Lightweight projection for phase 1 of keyset order-history paging: just enough
 * to identify the page ({@code id}) and build the next cursor ({@code createdAt}),
 * without loading the order graph.
 */
public interface OrderKeysetRow {
    Long getId();

    Instant getCreatedAt();
}
