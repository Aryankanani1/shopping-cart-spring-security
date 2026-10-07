package com.aryan.spring_security_demo.catalog;

import com.aryan.spring_security_demo.common.exception.BadRequestException;

/**
 * Thrown when an uploaded file is not an acceptable product image — empty, or of
 * a type the storefront can't render. Rendered as {@code 400 Bad Request}.
 */
public class InvalidImageException extends BadRequestException {
    public InvalidImageException(String message) {
        super("Invalid image", message);
    }
}
