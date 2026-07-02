package com.ahmedsohail.transferservice.common;

/**
 * Raised when account-service rejects a debit because the sender lacks the funds.
 * Mapped to HTTP 422.
 */
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(Long accountId) {
        super("Insufficient funds in account " + accountId);
    }

    public InsufficientFundsException(String message) {
        super(message);
    }
}
