package com.ahmedsohail.accountservice.account;

import com.ahmedsohail.accountservice.common.InsufficientFundsException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A customer account holding a balance in a single currency.
 *
 * <p>Money is modelled with {@link BigDecimal} (never {@code double}) so that amounts
 * are exact. The entity guards its own invariant: a debit can never overdraw the balance.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String ownerName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Optimistic-lock version: guards against lost updates if two transfers touch this account concurrently. */
    @Version
    private Long version;

    /** Required by JPA; not for application use. */
    protected Account() {
    }

    public Account(String ownerName, String email, BigDecimal balance, String currency) {
        this.ownerName = ownerName;
        this.email = email;
        this.balance = balance;
        this.currency = currency;
        this.createdAt = Instant.now();
    }

    /**
     * Removes {@code amount} from the balance.
     *
     * @throws InsufficientFundsException if the balance is smaller than {@code amount}
     */
    public void debit(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(id, balance, amount);
        }
        this.balance = this.balance.subtract(amount);
    }

    /** Adds {@code amount} to the balance. */
    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    public Long getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getEmail() {
        return email;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
