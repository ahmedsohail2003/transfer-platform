package com.ahmedsohail.accountservice.common;

import java.math.BigDecimal;

/** Raised when an account does not hold enough funds to cover a debit. Mapped to HTTP 422. */
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(Long accountId, BigDecimal balance, BigDecimal requested) {
        super("Insufficient funds in account " + accountId
                + ": balance=" + balance + ", requested=" + requested);
    }
}
