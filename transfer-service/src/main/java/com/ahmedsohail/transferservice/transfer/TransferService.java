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
 */
@Service
public class TransferService {

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
            compensate(fromId, request.amount(), from.currency(), creditFailure);
            Transfer failed = transferRepository.save(new Transfer(
                    fromId, toId, request.amount(), from.currency(), TransferStatus.FAILED, request.memo()));
            log.warn("Transfer id={} recorded as FAILED: {} {} from account {} to account {}",
                    failed.getId(), request.amount(), from.currency(), fromId, toId);
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
     * left to retry automatically, so it is logged loudly for manual reconciliation rather
     * than masking the original credit failure.
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

    public Transfer getTransfer(Long id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id));
    }

    public List<Transfer> historyFor(Long accountId) {
        return transferRepository.findByFromAccountIdOrToAccountIdOrderByCreatedAtDescIdDesc(accountId, accountId);
    }
}
