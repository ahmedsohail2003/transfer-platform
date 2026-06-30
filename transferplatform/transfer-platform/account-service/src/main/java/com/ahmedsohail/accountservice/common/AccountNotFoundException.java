package com.ahmedsohail.accountservice.common;

/** Raised when no account exists for a given id. Mapped to HTTP 404. */
public class AccountNotFoundException extends NotFoundException {
    public AccountNotFoundException(Long id) {
        super("Account not found: id=" + id);
    }
}
