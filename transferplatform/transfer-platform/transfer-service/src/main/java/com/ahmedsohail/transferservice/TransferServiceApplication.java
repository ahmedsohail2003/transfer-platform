package com.ahmedsohail.transferservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Transfer Service: records transfers and orchestrates money
 * movement by calling the account-service over HTTP. It owns no Account table.
 */
@SpringBootApplication
public class TransferServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransferServiceApplication.class, args);
    }
}
