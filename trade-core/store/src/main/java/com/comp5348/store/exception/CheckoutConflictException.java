package com.comp5348.store.exception;

/** The caller must refresh or correct the request; it must not silently submit again. */
public class CheckoutConflictException extends IllegalStateException {
    public CheckoutConflictException(String message) { super(message); }
}
