package com.aryan.spring_security_demo.identity;

/**
 * Published just before an account is deleted, inside the delete transaction, so
 * other modules can settle what still refers to it first (orders cancels the ones
 * still open). If the delete then fails, their changes roll back with it.
 */
public record UserDeletingEvent(Long userId) {
}
