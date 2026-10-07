package com.aryan.spring_security_demo.catalog;

/**
 * An image's stored file, read out of the database: what the download endpoint
 * sends back. A plain value, so the controller never touches the {@link Image}
 * entity or its BLOB after the transaction has closed.
 */
public record ImageFile(String fileName, String contentType, byte[] content) {
}
