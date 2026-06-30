package com.ahmedsohail.transferservice.client;

import com.ahmedsohail.transferservice.client.dto.AccountDto;
import com.ahmedsohail.transferservice.common.AccountNotFoundException;
import com.ahmedsohail.transferservice.common.InsufficientFundsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests {@link AccountClient}'s HTTP-to-domain-exception mapping. A {@link MockRestServiceServer}
 * stands in for account-service, so the {@code onStatus} handlers are exercised without a real
 * service running.
 */
class AccountClientTest {

    private MockRestServiceServer server;
    private AccountClient accountClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8081");
        server = MockRestServiceServer.bindTo(builder).build();
        accountClient = new AccountClient(builder.build());
    }

    @Test
    void getAccount_returnsMappedDto() {
        server.expect(requestTo("http://localhost:8081/api/accounts/1"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":1,"ownerName":"Alice","email":"alice@example.com",
                         "balance":100.00,"currency":"CAD","createdAt":"2024-01-01T00:00:00Z"}
                        """, MediaType.APPLICATION_JSON));

        AccountDto account = accountClient.getAccount(1L);

        assertThat(account.id()).isEqualTo(1L);
        assertThat(account.currency()).isEqualTo("CAD");
        assertThat(account.balance()).isEqualByComparingTo("100.00");
        server.verify();
    }

    @Test
    void getAccount_when404_throwsAccountNotFound() {
        server.expect(requestTo("http://localhost:8081/api/accounts/99"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> accountClient.getAccount(99L))
                .isInstanceOf(AccountNotFoundException.class);
        server.verify();
    }

    @Test
    void debit_when422_throwsInsufficientFunds() {
        server.expect(requestTo("http://localhost:8081/api/accounts/1/debit"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> accountClient.debit(1L, new BigDecimal("30.00")))
                .isInstanceOf(InsufficientFundsException.class);
        server.verify();
    }

    @Test
    void debit_when404_throwsAccountNotFound() {
        server.expect(requestTo("http://localhost:8081/api/accounts/99/debit"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> accountClient.debit(99L, new BigDecimal("30.00")))
                .isInstanceOf(AccountNotFoundException.class);
        server.verify();
    }

    @Test
    void credit_success_returnsUpdatedAccount() {
        server.expect(requestTo("http://localhost:8081/api/accounts/2/credit"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"id":2,"ownerName":"Bob","email":"bob@example.com",
                         "balance":50.00,"currency":"CAD","createdAt":"2024-01-01T00:00:00Z"}
                        """, MediaType.APPLICATION_JSON));

        AccountDto account = accountClient.credit(2L, new BigDecimal("30.00"));

        assertThat(account.id()).isEqualTo(2L);
        assertThat(account.balance()).isEqualByComparingTo("50.00");
        server.verify();
    }

    @Test
    void credit_when404_throwsAccountNotFound() {
        server.expect(requestTo("http://localhost:8081/api/accounts/99/credit"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> accountClient.credit(99L, new BigDecimal("30.00")))
                .isInstanceOf(AccountNotFoundException.class);
        server.verify();
    }
}
