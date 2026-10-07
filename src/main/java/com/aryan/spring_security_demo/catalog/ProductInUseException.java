package com.aryan.spring_security_demo.catalog;

import com.aryan.spring_security_demo.common.exception.ConflictException;

/** The product is still referenced (by past orders), so it can't be deleted. Mapped to 409. */
public class ProductInUseException extends ConflictException {
    public ProductInUseException(String message) {
        super("Product in use", message);
    }
}
