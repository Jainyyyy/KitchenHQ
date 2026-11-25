package com.rasoiroute.api;

/** A problem with a request, carrying the HTTP status code to answer with (400 = bad input, 404 = not found ...). */
public final class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
