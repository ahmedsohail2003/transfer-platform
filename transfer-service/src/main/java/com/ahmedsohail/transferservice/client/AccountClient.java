package com.ahmedsohail.transferservice.client;

import com.ahmedsohail.transferservice.client.dto.AccountDto;
import com.ahmedsohail.transferservice.client.dto.AmountRequest;
import com.ahmedsohail.transferservice.common.AccountNotFoundException;
import com.ahmedsohail.transferservice.common.InsufficientFundsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * HTTP client for account-service. Wraps a {@link RestClient} and translates the
 * service's HTTP error responses into this service's domain exceptions:
 * 404 -> {@link AccountNotFoundException}, 422 -> {@link InsufficientFundsException}.
 * This service owns no Account table; all balance changes happen here, over HTTP.
 */
@Component
public class AccountClient {

    private static final Logger log = LoggerFactory.getLogger(AccountClient.class);

    private final RestClient restClient;

    public AccountClient(RestClient accountServiceRestClient) {
        this.restClient = accountServiceRestClient;
    }

    /** Fetches an account; maps a 404 from account-service to {@link AccountNotFoundException}. */
    public AccountDto getAccount(Long id) {
        log.debug("Fetching account {} from account-service", id);
        return restClient.get()
                .uri("/api/accounts/{id}", id)
                .retrieve()
                .onStatus(notFound(), (req, res) -> {
                    throw new AccountNotFoundException(id);
                })
                .body(AccountDto.class);
    }

    /**
     * Debits an account. Maps 404 -> {@link AccountNotFoundException} and
     * 422 -> {@link InsufficientFundsException} (the no-overdraft invariant lives
     * in account-service).
     */
    public AccountDto debit(Long id, BigDecimal amount) {
        log.debug("Debiting {} from account {} via account-service", amount, id);
        return restClient.post()
                .uri("/api/accounts/{id}/debit", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AmountRequest(amount))
                .retrieve()
                .onStatus(notFound(), (req, res) -> {
                    throw new AccountNotFoundException(id);
                })
                .onStatus(unprocessable(), (req, res) -> {
                    throw new InsufficientFundsException(id);
                })
                .body(AccountDto.class);
    }

    /** Credits an account. Maps 404 -> {@link AccountNotFoundException}. */
    public AccountDto credit(Long id, BigDecimal amount) {
        log.debug("Crediting {} to account {} via account-service", amount, id);
        return restClient.post()
                .uri("/api/accounts/{id}/credit", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AmountRequest(amount))
                .retrieve()
                .onStatus(notFound(), (req, res) -> {
                    throw new AccountNotFoundException(id);
                })
                .body(AccountDto.class);
    }

    private static java.util.function.Predicate<HttpStatusCode> notFound() {
        return status -> status.value() == 404;
    }

    private static java.util.function.Predicate<HttpStatusCode> unprocessable() {
        return status -> status.value() == 422;
    }
}
