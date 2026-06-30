package com.ahmedsohail.transferservice.common;

/** Raised when account-service reports an account does not exist. Mapped to HTTP 404. */
public class AccountNotFoundException extends NotFoundException {
    public AccountNotFoundException(Long id) {
        super("Account not found: id=" + id);
    }
}
