package com.ahmedsohail.transferservice.common;

/** Base type for "resource does not exist" errors. Mapped to HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
