package com.ahmedsohail.transferservice.transfer;

import com.ahmedsohail.transferservice.client.AccountClient;
import com.ahmedsohail.transferservice.client.dto.AccountDto;
import com.ahmedsohail.transferservice.common.InvalidTransferException;
import com.ahmedsohail.transferservice.common.TransferFailedException;
import com.ahmedsohail.transferservice.common.TransferNotFoundException;
import com.ahmedsohail.transferservice.transfer.dto.TransferRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates money movement across the account-service. This service owns no Account
 * table: it debits the sender and credits the receiver over HTTP, then records the
 * outcome as a {@link Transfer} row.
 *
 * <p>Because the two balance changes happen in separate services (not one local
 * transaction), the credit step is guarded by <em>compensation</em>: if crediting the
 * receiver fails after the sender has been debited, the sender is refunded, a
 * {@code FAILED} transfer is recorded, and a {@link TransferFailedException} is raised.
 *
 * <p>Every transfer that reaches the money-movement step is persisted as either
 * {@code COMPLETED} or {@code FAILED} — including the worst case where the compensating
 * refund itself fails. That row is flagged {@value #REQUIRES_RECONCILIATION} in its
 * failure reason and saved <em>before</em> the refund failure propagates, so the one
 * incident that most needs manual follow-up can never go missing from the audit trail.
 */
@Service
public class TransferService {

    /**
     * Marker prefixed to a {@link Transfer#getFailureReason() failure reason} when the
     * compensating refund failed after a failed credit — the sender may still be debited,
     * so an operator must reconcile the balances manually.
     */
    public static final String REQUIRES_RECONCILIATION = "REQUIRES_RECONCILIATION";

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final AccountClient accountClient;
    private final TransferRepository transferRepository;

    public TransferService(AccountClient accountClient, TransferRepository transferRepository) {
        this.accountClient = accountClient;
        this.transferRepository = transferRepository;
    }

    public Transfer transfer(TransferRequest request) {
        Long fromId = request.fromAccountId();
        Long toId = request.toAccountId();

        // 1) Reject a transfer to the same account before touching any account.
        if (fromId.equals(toId)) {
            throw new InvalidTransferException("Cannot transfer to the same account");
        }

        // 2) Resolve both accounts (a missing account surfaces as 404).
        AccountDto from = accountClient.getAccount(fromId);
        AccountDto to = accountClient.getAccount(toId);

        // 3) Reject cross-currency transfers.
        if (!from.currency().equals(to.currency())) {
            throw new InvalidTransferException(
                    "Currency mismatch: " + from.currency() + " -> " + to.currency());
        }

        // 4) Debit the sender first: insufficient funds aborts here (422), nothing moved.
        accountClient.debit(fromId, request.amount());
        log.info("Debited {} {} from account {} for transfer", request.amount(), from.currency(), fromId);

        // 5) Credit the receiver; on failure, compensate by refunding the sender.
        try {
            accountClient.credit(toId, request.amount());
        } catch (RuntimeException creditFailure) {
            log.error("Credit to account {} failed; compensating by refunding account {}", toId, fromId,
                    creditFailure);
            try {
                compensate(fromId, request.amount(), from.currency(), creditFailure);
            } catch (RuntimeException refundFailure) {
                // Worst case: the sender was debited, the credit failed, AND the refund
                // failed — money may be missing. Persist the FAILED row BEFORE rethrowing,
                // flagged for manual reconciliation; this is the one outcome the audit
                // trail must never lose.
                recordFailedForReconciliation(request, from.currency(), refundFailure);
                throw refundFailure;
            }
            recordFailed(request, from.currency(),
                    "Credit to account " + toId + " failed; sender was refunded");
            throw new TransferFailedException(
                    "Could not credit account " + toId + "; sender was refunded", creditFailure);
        }

        // 6) Both legs succeeded: record the completed transfer.
        Transfer transfer = transferRepository.save(new Transfer(
                fromId, toId, request.amount(), from.currency(), TransferStatus.COMPLETED, request.memo()));
        log.info("Transfer id={} completed: {} {} from account {} to account {}",
                transfer.getId(), request.amount(), from.currency(), fromId, toId);
        return transfer;
    }

    /**
     * Refunds the sender after a failed credit. If the refund itself fails there is nothing
     * left to retry automatically, so it is logged loudly (with the original credit failure
     * attached as suppressed) and rethrown rather than masking the original credit failure —
     * and the caller persists the {@code FAILED} transfer row before letting it propagate.
     */
    private void compensate(Long fromId, java.math.BigDecimal amount, String currency, RuntimeException cause) {
        try {
            accountClient.credit(fromId, amount);
            log.info("Compensated: refunded {} {} to account {}", amount, currency, fromId);
        } catch (RuntimeException refundFailure) {
            log.error("CRITICAL: refund of {} {} to account {} failed after credit failure; "
                    + "manual reconciliation required", amount, currency, fromId, refundFailure);
            refundFailure.addSuppressed(cause);
            throw refundFailure;
        }
    }

    /**
     * Persists the {@code FAILED} row for the double-failure case (credit failed, refund
     * failed) with a {@value #REQUIRES_RECONCILIATION} marker. Best effort by design: if
     * even this audit write fails, the persistence error is logged and attached to the
     * refund failure as suppressed — it must never replace or mask the incident itself.
     */
    private void recordFailedForReconciliation(TransferRequest request, String currency,
                                               RuntimeException refundFailure) {
        try {
            recordFailed(request, currency, REQUIRES_RECONCILIATION + ": credit to account "
                    + request.toAccountId() + " failed and refund to account "
                    + request.fromAccountId() + " also failed; sender may still be debited");
        } catch (RuntimeException persistFailure) {
            log.error("CRITICAL: could not persist FAILED transfer ({} {} from account {} to account {}); "
                            + "manual reconciliation must rely on these logs", request.amount(), currency,
                    request.fromAccountId(), request.toAccountId(), persistFailure);
            refundFailure.addSuppressed(persistFailure);
        }
    }

    /** Persists and logs a {@code FAILED} transfer row so failed attempts stay auditable. */
    private Transfer recordFailed(TransferRequest request, String currency, String failureReason) {
        Transfer failed = transferRepository.save(new Transfer(
                request.fromAccountId(), request.toAccountId(), request.amount(), currency,
                TransferStatus.FAILED, request.memo(), failureReason));
        log.warn("Transfer id={} recorded as FAILED ({} {} from account {} to account {}): {}",
                failed.getId(), request.amount(), currency,
                request.fromAccountId(), request.toAccountId(), failureReason);
        return failed;
    }

    public Transfer getTransfer(Long id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id));
    }

    public List<Transfer> historyFor(Long accountId) {
        return transferRepository.findByFromAccountIdOrToAccountIdOrderByCreatedAtDescIdDesc(accountId, accountId);
    }
}
