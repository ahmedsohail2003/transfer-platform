package com.ahmedsohail.transferservice.common;

/** Raised when a transfer request is structurally invalid (e.g. same account, currency mismatch). Mapped to HTTP 400. */
public class InvalidTransferException extends RuntimeException {
    public InvalidTransferException(String message) {
        super(message);
    }
}
