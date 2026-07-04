package com.ahmedsohail.transferservice.transfer;

import com.ahmedsohail.transferservice.client.AccountClient;
import com.ahmedsohail.transferservice.client.dto.AccountDto;
import com.ahmedsohail.transferservice.common.AccountNotFoundException;
import com.ahmedsohail.transferservice.common.InsufficientFundsException;
import com.ahmedsohail.transferservice.common.InvalidTransferException;
import com.ahmedsohail.transferservice.common.TransferFailedException;
import com.ahmedsohail.transferservice.common.TransferNotFoundException;
import com.ahmedsohail.transferservice.transfer.dto.TransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the transfer orchestration logic. The {@link AccountClient} and the
 * repository are mocked, so these exercise business rules and the compensation flow only —
 * no Spring context, no database, and no running account-service.
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountClient accountClient;

    @Mock
    private TransferRepository transferRepository;

    @InjectMocks
    private TransferService transferService;

    private AccountDto sender;
    private AccountDto receiver;

    @BeforeEach
    void setUp() {
        sender = new AccountDto(1L, "Sender", "sender@example.com", new BigDecimal("100.00"), "CAD", Instant.now());
        receiver = new AccountDto(2L, "Receiver", "receiver@example.com", new BigDecimal("20.00"), "CAD", Instant.now());
    }

    @Test
    void transfer_movesFundsViaClientAndRecordsCompleted() {
        when(accountClient.getAccount(1L)).thenReturn(sender);
        when(accountClient.getAccount(2L)).thenReturn(receiver);
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transfer result = transferService.transfer(new TransferRequest(1L, 2L, new BigDecimal("30.00"), "rent"));

        verify(accountClient).debit(1L, new BigDecimal("30.00"));
        verify(accountClient).credit(2L, new BigDecimal("30.00"));
        assertThat(result.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(result.getAmount()).isEqualByComparingTo("30.00");
        assertThat(result.getCurrency()).isEqualTo("CAD");
        assertThat(result.getFromAccountId()).isEqualTo(1L);
        assertThat(result.getToAccountId()).isEqualTo(2L);
        assertThat(result.getFailureReason()).isNull();
    }

    @Test
    void transfer_withInsufficientFundsFromClient_throwsAndNeverCredits() {
        when(accountClient.getAccount(1L)).thenReturn(sender);
        when(accountClient.getAccount(2L)).thenReturn(receiver);
        when(accountClient.debit(eq(1L), any(BigDecimal.class)))
                .thenThrow(new InsufficientFundsException(1L));

        assertThatThrownBy(() ->
                transferService.transfer(new TransferRequest(1L, 2L, new BigDecimal("500.00"), null)))
                .isInstanceOf(InsufficientFundsException.class);

        verify(accountClient, never()).credit(any(), any());
        verify(transferRepository, never()).save(any());
    }

    @Test
    void transfer_toSameAccount_isRejectedBeforeAnyClientCall() {
        assertThatThrownBy(() ->
                transferService.transfer(new TransferRequest(1L, 1L, new BigDecimal("10.00"), null)))
                .isInstanceOf(InvalidTransferException.class)
                .hasMessageContaining("same account");

        verifyNoInteractions(accountClient);
        verifyNoInteractions(transferRepository);
    }

    @Test
    void transfer_fromUnknownAccount_throwsNotFound() {
        when(accountClient.getAccount(1L)).thenThrow(new AccountNotFoundException(1L));

        assertThatThrownBy(() ->
                transferService.transfer(new TransferRequest(1L, 2L, new BigDecimal("10.00"), null)))
                .isInstanceOf(AccountNotFoundException.class);

        verify(accountClient, never()).debit(any(), any());
        verify(transferRepository, never()).save(any());
    }

    @Test
    void transfer_withCurrencyMismatch_isRejectedBeforeDebit() {
        AccountDto usdAccount =
                new AccountDto(2L, "Receiver", "usd@example.com", new BigDecimal("0.00"), "USD", Instant.now());
        when(accountClient.getAccount(1L)).thenReturn(sender); // CAD
        when(accountClient.getAccount(2L)).thenReturn(usdAccount);

        assertThatThrownBy(() ->
                transferService.transfer(new TransferRequest(1L, 2L, new BigDecimal("10.00"), null)))
                .isInstanceOf(InvalidTransferException.class)
                .hasMessageContaining("Currency mismatch");

        verify(accountClient, never()).debit(any(), any());
        verify(transferRepository, never()).save(any());
    }

    @Test
    void transfer_whenCreditFails_compensatesSenderAndRecordsFailed() {
        when(accountClient.getAccount(1L)).thenReturn(sender);
        when(accountClient.getAccount(2L)).thenReturn(receiver);
        when(accountClient.credit(eq(2L), any(BigDecimal.class)))
                .thenThrow(new RuntimeException("account-service unavailable"));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() ->
                transferService.transfer(new TransferRequest(1L, 2L, new BigDecimal("30.00"), "rent")))
                .isInstanceOf(TransferFailedException.class);

        // The sender was debited, then refunded (compensation).
        verify(accountClient).debit(1L, new BigDecimal("30.00"));
        verify(accountClient).credit(1L, new BigDecimal("30.00")); // refund
        verify(accountClient).credit(2L, new BigDecimal("30.00")); // failed attempt

        // A FAILED transfer is persisted for auditability.
        ArgumentCaptor<Transfer> saved = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(saved.getValue().getFromAccountId()).isEqualTo(1L);
        assertThat(saved.getValue().getToAccountId()).isEqualTo(2L);
        assertThat(saved.getValue().getFailureReason()).contains("sender was refunded");
    }

    @Test
    void transfer_whenCreditAndRefundBothFail_stillRecordsFailedRowAndPropagates() {
        when(accountClient.getAccount(1L)).thenReturn(sender);
        when(accountClient.getAccount(2L)).thenReturn(receiver);
        RuntimeException creditFailure = new RuntimeException("credit failed: account-service unavailable");
        RuntimeException refundFailure = new RuntimeException("refund failed: account-service still unavailable");
        when(accountClient.credit(eq(2L), any(BigDecimal.class))).thenThrow(creditFailure);
        when(accountClient.credit(eq(1L), any(BigDecimal.class))).thenThrow(refundFailure);
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // The refund failure still propagates — nothing may mask the incident — carrying
        // the original credit failure as a suppressed exception.
        assertThatThrownBy(() ->
                transferService.transfer(new TransferRequest(1L, 2L, new BigDecimal("30.00"), "rent")))
                .isSameAs(refundFailure)
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).contains(creditFailure));

        // AND the worst-case outcome (sender debited, refund failed) is still persisted as
        // a FAILED row, flagged for manual reconciliation — the audit trail must never lose
        // exactly this incident.
        ArgumentCaptor<Transfer> saved = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository).save(saved.capture());
        Transfer failedRow = saved.getValue();
        assertThat(failedRow.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(failedRow.getFromAccountId()).isEqualTo(1L);
        assertThat(failedRow.getToAccountId()).isEqualTo(2L);
        assertThat(failedRow.getAmount()).isEqualByComparingTo("30.00");
        assertThat(failedRow.getFailureReason()).startsWith(TransferService.REQUIRES_RECONCILIATION);
    }

    @Test
    void transfer_whenFailedRowCannotBePersisted_refundFailureStillPropagates() {
        when(accountClient.getAccount(1L)).thenReturn(sender);
        when(accountClient.getAccount(2L)).thenReturn(receiver);
        RuntimeException creditFailure = new RuntimeException("credit failed");
        RuntimeException refundFailure = new RuntimeException("refund failed");
        RuntimeException persistFailure = new RuntimeException("database unavailable");
        when(accountClient.credit(eq(2L), any(BigDecimal.class))).thenThrow(creditFailure);
        when(accountClient.credit(eq(1L), any(BigDecimal.class))).thenThrow(refundFailure);
        when(transferRepository.save(any(Transfer.class))).thenThrow(persistFailure);

        // Even if the audit write itself fails, the save was attempted and the refund
        // failure (not the persistence error) is what propagates, with the other two
        // failures attached as suppressed.
        assertThatThrownBy(() ->
                transferService.transfer(new TransferRequest(1L, 2L, new BigDecimal("30.00"), null)))
                .isSameAs(refundFailure)
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).contains(creditFailure, persistFailure));

        verify(transferRepository).save(any(Transfer.class));
    }

    @Test
    void getTransfer_returnsTransferWhenPresent() {
        Transfer saved = new Transfer(1L, 2L, new BigDecimal("30.00"), "CAD", TransferStatus.COMPLETED, "rent");
        when(transferRepository.findById(7L)).thenReturn(Optional.of(saved));

        assertThat(transferService.getTransfer(7L)).isSameAs(saved);
    }

    @Test
    void getTransfer_whenMissing_throwsNotFound() {
        when(transferRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.getTransfer(99L))
                .isInstanceOf(TransferNotFoundException.class)
                .hasMessageContaining("99");
    }
}
