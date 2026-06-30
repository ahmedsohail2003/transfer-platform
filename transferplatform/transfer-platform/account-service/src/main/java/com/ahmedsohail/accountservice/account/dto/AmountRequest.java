package com.ahmedsohail.accountservice.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request body for the debit and credit endpoints. A positive, money-shaped amount
 * (at most 2 decimal places). Bean Validation rejects anything else with HTTP 400.
 */
public record AmountRequest(
        @NotNull
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount
) {
}
