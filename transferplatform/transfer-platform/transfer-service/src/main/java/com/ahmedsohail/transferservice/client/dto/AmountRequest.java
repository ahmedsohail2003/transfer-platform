package com.ahmedsohail.transferservice.client.dto;

import java.math.BigDecimal;

/**
 * Body sent to account-service's debit/credit endpoints: {@code {"amount": ...}}.
 * Matches the shape account-service expects when moving money on an account.
 */
public record AmountRequest(BigDecimal amount) {
}
