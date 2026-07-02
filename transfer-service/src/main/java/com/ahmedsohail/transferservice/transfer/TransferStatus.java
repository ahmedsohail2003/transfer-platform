package com.ahmedsohail.transferservice.transfer;

/**
 * Outcome of a transfer.
 * <ul>
 *   <li>{@code COMPLETED} — the sender was debited and the receiver credited.</li>
 *   <li>{@code FAILED} — the sender was debited but the receiver could not be
 *       credited, so the debit was compensated (refunded); the row records the
 *       attempt for auditability.</li>
 * </ul>
 */
public enum TransferStatus {
    COMPLETED,
    FAILED
}
