package com.ahmedsohail.transferservice.transfer.dto;

import com.ahmedsohail.transferservice.transfer.Transfer;
import com.ahmedsohail.transferservice.transfer.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** API view of a {@link Transfer}. {@code failureReason} is {@code null} unless the transfer FAILED. */
public record TransferResponse(
        Long id,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        String currency,
        TransferStatus status,
        String failureReason,
        String memo,
        Instant createdAt
) {
    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getFromAccountId(),
                transfer.getToAccountId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getStatus(),
                transfer.getFailureReason(),
                transfer.getMemo(),
                transfer.getCreatedAt()
        );
    }
}
