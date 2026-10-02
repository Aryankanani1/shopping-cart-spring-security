package com.aryan.spring_security_demo.catalog;

/** The product is still referenced (by past orders), so it can't be deleted. Mapped to 409. */
public class ProductInUseException extends RuntimeException {
    public ProductInUseException(String message) {
        super(message);
    }
}
