package com.ahmedsohail.transferservice.common;

/**
 * Raised when money movement could not be completed downstream — the sender was
 * debited but the receiver could not be credited, so the debit was compensated
 * (refunded) and the transfer recorded as FAILED. Mapped to HTTP 502 Bad Gateway,
 * since the failure originates in a dependency (account-service).
 */
public class TransferFailedException extends RuntimeException {
    public TransferFailedException(String message) {
        super(message);
    }

    public TransferFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
