package com.aryan.spring_security_demo.catalog;

/**
 * Published just before a product is deleted, inside the delete transaction, so
 * other modules (carts) can drop their references to it first. If the delete
 * then fails, their changes roll back with it.
 */
public record ProductDeletingEvent(Long productId) {
}
