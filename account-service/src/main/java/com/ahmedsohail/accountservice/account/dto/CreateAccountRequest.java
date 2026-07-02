package com.ahmedsohail.accountservice.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Request body for opening a new account. Bean Validation rejects malformed input with HTTP 400. */
public record CreateAccountRequest(
        @NotBlank @Size(max = 100, message = "ownerName must be at most 100 characters") String ownerName,
        @NotBlank @Email @Size(max = 254, message = "email must be at most 254 characters") String email,
        @NotNull
        @DecimalMin(value = "0.00", message = "openingBalance cannot be negative")
        @Digits(integer = 17, fraction = 2, message = "openingBalance must have at most 2 decimal places")
        BigDecimal openingBalance,
        @NotBlank @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code") String currency
) {
}
