package com.rockey.hospitality.exception;

public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh session is invalid or expired.");
    }
}
