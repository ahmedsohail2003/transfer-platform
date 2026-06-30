package com.ahmedsohail.accountservice.account;

import com.ahmedsohail.accountservice.account.dto.CreateAccountRequest;
import com.ahmedsohail.accountservice.common.AccountNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** Application logic for creating, reading, and moving money on accounts. */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public Account createAccount(CreateAccountRequest request) {
        Account account = new Account(
                request.ownerName(),
                request.email(),
                request.openingBalance(),
                request.currency().toUpperCase(Locale.ROOT)
        );
        Account saved = accountRepository.save(account);
        log.info("Created account id={} owner='{}' currency={}",
                saved.getId(), saved.getOwnerName(), saved.getCurrency());
        return saved;
    }

    @Transactional(readOnly = true)
    public Account getAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Account> listAccounts() {
        return accountRepository.findAll();
    }

    /**
     * Removes {@code amount} from the account's balance.
     *
     * @throws AccountNotFoundException     if no account has the given id (HTTP 404)
     * @throws com.ahmedsohail.accountservice.common.InsufficientFundsException
     *         if the balance cannot cover the amount (HTTP 422)
     */
    @Transactional
    public Account debit(Long id, BigDecimal amount) {
        Account account = getAccount(id);
        account.debit(amount);
        Account saved = accountRepository.save(account);
        log.info("Debited account id={} amount={} newBalance={}", id, amount, saved.getBalance());
        return saved;
    }

    /**
     * Adds {@code amount} to the account's balance.
     *
     * @throws AccountNotFoundException if no account has the given id (HTTP 404)
     */
    @Transactional
    public Account credit(Long id, BigDecimal amount) {
        Account account = getAccount(id);
        account.credit(amount);
        Account saved = accountRepository.save(account);
        log.info("Credited account id={} amount={} newBalance={}", id, amount, saved.getBalance());
        return saved;
    }
}
