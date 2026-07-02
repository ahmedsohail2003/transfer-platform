package com.ahmedsohail.accountservice.account.dto;

import com.ahmedsohail.accountservice.account.Account;

import java.math.BigDecimal;
import java.time.Instant;

/** API view of an {@link Account}. Keeps the JPA entity out of the public contract. */
public record AccountResponse(
        Long id,
        String ownerName,
        String email,
        BigDecimal balance,
        String currency,
        Instant createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getOwnerName(),
                account.getEmail(),
                account.getBalance(),
                account.getCurrency(),
                account.getCreatedAt()
        );
    }
}
