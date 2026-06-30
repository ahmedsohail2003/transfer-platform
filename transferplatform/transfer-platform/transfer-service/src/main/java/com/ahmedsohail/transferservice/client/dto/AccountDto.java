package com.ahmedsohail.transferservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * View of an account as returned by account-service's {@code AccountResponse}.
 * This service owns no Account table; this record is a read-only projection used
 * only for orchestration (currency check, identifiers). Unknown fields are ignored
 * so the contract can evolve additively without breaking this client.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountDto(
        Long id,
        String ownerName,
        String email,
        BigDecimal balance,
        String currency,
        Instant createdAt
) {
}
