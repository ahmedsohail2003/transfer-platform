package com.ahmedsohail.accountservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Account Service: owns customer accounts and balances, and
 * enforces the no-overdraft invariant on every debit. Other services move money by
 * calling this service's HTTP debit/credit endpoints.
 */
@SpringBootApplication
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
