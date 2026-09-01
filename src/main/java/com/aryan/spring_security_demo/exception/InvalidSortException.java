package com.aryan.spring_security_demo.exception;

import java.util.Collection;

/**
 * Thrown when a client requests sorting on a field that is not on the endpoint's
 * allowlist. Clients supply sort fields by name, so an unrestricted sort would let
 * callers order by arbitrary (possibly unindexed or internal) columns; this keeps
 * sorting to a known, indexed set. Mapped to 400 by the global handler.
 */
public class InvalidSortException extends RuntimeException {
    public InvalidSortException(String property, Collection<String> allowed) {
        super("Unsupported sort property '" + property + "'. Allowed sort fields: " + allowed);
    }
}
