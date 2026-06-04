package com.opendb.exception;

public class OpenDbException extends RuntimeException {

    public OpenDbException(String message) {
        super(message);
    }

    public OpenDbException(String message, Throwable cause) {
        super(message, cause);
    }
}
