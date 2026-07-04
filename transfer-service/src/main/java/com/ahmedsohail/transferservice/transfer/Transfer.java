package com.ahmedsohail.transferservice.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** An immutable record of one money movement between two accounts. */
@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long fromAccountId;

    @Column(nullable = false)
    private Long toAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    @Column(length = 140)
    private String memo;

    /**
     * Why a {@code FAILED} transfer failed; {@code null} for {@code COMPLETED} transfers.
     * When the compensating refund itself failed, the reason starts with the
     * {@code REQUIRES_RECONCILIATION} marker so operators can find rows that need a
     * manual balance fix.
     */
    @Column(length = 255)
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA; not for application use. */
    protected Transfer() {
    }

    public Transfer(Long fromAccountId, Long toAccountId, BigDecimal amount, String currency,
                    TransferStatus status, String memo) {
        this(fromAccountId, toAccountId, amount, currency, status, memo, null);
    }

    public Transfer(Long fromAccountId, Long toAccountId, BigDecimal amount, String currency,
                    TransferStatus status, String memo, String failureReason) {
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.memo = memo;
        this.failureReason = failureReason;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getFromAccountId() {
        return fromAccountId;
    }

    public Long getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getMemo() {
        return memo;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
