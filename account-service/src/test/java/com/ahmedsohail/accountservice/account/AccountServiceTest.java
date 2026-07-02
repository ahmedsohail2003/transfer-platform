package com.ahmedsohail.accountservice.account;

import com.ahmedsohail.accountservice.account.dto.CreateAccountRequest;
import com.ahmedsohail.accountservice.common.AccountNotFoundException;
import com.ahmedsohail.accountservice.common.InsufficientFundsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void createAccount_normalizesCurrencyAndPersists() {
        CreateAccountRequest request =
                new CreateAccountRequest("Ada Lovelace", "ada@example.com", new BigDecimal("100.00"), "cad");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account result = accountService.createAccount(request);

        assertThat(result.getOwnerName()).isEqualTo("Ada Lovelace");
        assertThat(result.getCurrency()).isEqualTo("CAD"); // lower-case input is normalized
        assertThat(result.getBalance()).isEqualByComparingTo("100.00");
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void getAccount_whenMissing_throwsNotFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(99L))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void debit_withSufficientFunds_reducesBalance() {
        Account account = new Account("Bob", "bob@example.com", new BigDecimal("100.00"), "CAD");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account result = accountService.debit(1L, new BigDecimal("30.00"));

        assertThat(result.getBalance()).isEqualByComparingTo("70.00");
        verify(accountRepository).save(account);
    }

    @Test
    void debit_withInsufficientFunds_throwsAndDoesNotSave() {
        Account account = new Account("Bob", "bob@example.com", new BigDecimal("10.00"), "CAD");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.debit(1L, new BigDecimal("50.00")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");

        assertThat(account.getBalance()).isEqualByComparingTo("10.00"); // unchanged
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void credit_increasesBalance() {
        Account account = new Account("Bob", "bob@example.com", new BigDecimal("100.00"), "CAD");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account result = accountService.credit(1L, new BigDecimal("25.50"));

        assertThat(result.getBalance()).isEqualByComparingTo("125.50");
        verify(accountRepository).save(account);
    }
}
