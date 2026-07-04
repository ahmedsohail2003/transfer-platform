package com.ahmedsohail.transferservice.transfer;

/**
 * Outcome of a transfer.
 * <ul>
 *   <li>{@code COMPLETED} — the sender was debited and the receiver credited.</li>
 *   <li>{@code FAILED} — the sender was debited but the receiver could not be
 *       credited; the row records the attempt for auditability, and its failure
 *       reason says what happened next: normally the debit was compensated
 *       (refunded), but if the refund itself also failed the reason carries a
 *       {@code REQUIRES_RECONCILIATION} marker so operators know the balances
 *       must be fixed manually.</li>
 * </ul>
 */
public enum TransferStatus {
    COMPLETED,
    FAILED
}
