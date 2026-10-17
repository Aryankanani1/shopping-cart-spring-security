package com.aryan.spring_security_demo.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * This database's id ({@code V16__installation.sql}): random, and created once with
 * the database. Stripe keeps idempotency keys and sends webhooks per account, while
 * a payment id is unique only within one database, so the Stripe adapter puts this
 * id in its keys and metadata. Databases sharing an account (a local one created
 * again, another developer's) then can't receive each other's answers or events.
 */
@Component
@RequiredArgsConstructor
public class Installation {

    private final JdbcTemplate jdbc;

    /** Read once: it never changes. */
    private volatile String id;

    public String id() {
        String known = id;
        if (known == null) {
            known = jdbc.queryForObject("select id from installation", String.class);
            id = known;
        }
        return known;
    }
}
