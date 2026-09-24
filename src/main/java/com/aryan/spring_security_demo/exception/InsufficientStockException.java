package com.aryan.spring_security_demo.exception;

/**
 * Thrown when a cart line or an order asks for more units of a product than are
 * in stock. Rendered as {@code 409 Conflict}: the request is well-formed, but
 * conflicts with current inventory. The message names the product and how many
 * are left, so the storefront can show it to the shopper as-is.
 */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String productName, int available) {
        super(available <= 0
                ? productName + " is out of stock"
                : "Only " + available + " of " + productName + " left in stock");
    }
}
