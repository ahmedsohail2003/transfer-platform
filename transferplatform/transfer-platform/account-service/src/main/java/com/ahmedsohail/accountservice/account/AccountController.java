package com.ahmedsohail.accountservice.account;

import com.ahmedsohail.accountservice.account.dto.AccountResponse;
import com.ahmedsohail.accountservice.account.dto.AmountRequest;
import com.ahmedsohail.accountservice.account.dto.CreateAccountRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** REST endpoints for managing accounts and moving money in or out of them. */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @GetMapping
    public List<AccountResponse> list() {
        return accountService.listAccounts().stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AccountResponse getById(@PathVariable Long id) {
        return AccountResponse.from(accountService.getAccount(id));
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable Long id) {
        Account account = accountService.getAccount(id);
        return new BalanceResponse(account.getId(), account.getBalance(), account.getCurrency());
    }

    @PostMapping("/{id}/debit")
    public AccountResponse debit(@PathVariable Long id, @Valid @RequestBody AmountRequest request) {
        return AccountResponse.from(accountService.debit(id, request.amount()));
    }

    @PostMapping("/{id}/credit")
    public AccountResponse credit(@PathVariable Long id, @Valid @RequestBody AmountRequest request) {
        return AccountResponse.from(accountService.credit(id, request.amount()));
    }

    /** Lightweight projection for the balance endpoint. */
    public record BalanceResponse(Long accountId, BigDecimal balance, String currency) {
    }
}
