package com.ahmedsohail.accountservice.account;

import com.ahmedsohail.accountservice.account.dto.AmountRequest;
import com.ahmedsohail.accountservice.account.dto.CreateAccountRequest;
import com.ahmedsohail.accountservice.common.AccountNotFoundException;
import com.ahmedsohail.accountservice.common.InsufficientFundsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link AccountController}. The service is mocked, so these verify
 * HTTP wiring only: status codes, response mapping, validation, and error handling.
 */
@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountService accountService;

    @Test
    void postAccount_valid_returns201WithBody() throws Exception {
        when(accountService.createAccount(any(CreateAccountRequest.class)))
                .thenReturn(new Account("Ada Lovelace", "ada@example.com", new BigDecimal("100.00"), "CAD"));

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAccountRequest(
                                "Ada Lovelace", "ada@example.com", new BigDecimal("100.00"), "CAD"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.currency").value("CAD"))
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void getAccount_returns200() throws Exception {
        when(accountService.getAccount(1L))
                .thenReturn(new Account("Bob", "bob@example.com", new BigDecimal("50.00"), "CAD"));

        mockMvc.perform(get("/api/accounts/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerName").value("Bob"));
    }

    @Test
    void getAccount_whenMissing_returns404() throws Exception {
        when(accountService.getAccount(99L)).thenThrow(new AccountNotFoundException(99L));

        mockMvc.perform(get("/api/accounts/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getBalance_returns200() throws Exception {
        when(accountService.getAccount(1L))
                .thenReturn(new Account("Bob", "bob@example.com", new BigDecimal("50.00"), "CAD"));

        mockMvc.perform(get("/api/accounts/{id}/balance", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50.00))
                .andExpect(jsonPath("$.currency").value("CAD"));
    }

    @Test
    void postDebit_valid_returns200WithUpdatedBalance() throws Exception {
        when(accountService.debit(eq(1L), any(BigDecimal.class)))
                .thenReturn(new Account("Bob", "bob@example.com", new BigDecimal("60.00"), "CAD"));

        mockMvc.perform(post("/api/accounts/{id}/debit", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmountRequest(new BigDecimal("40.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(60.00));
    }

    @Test
    void postDebit_insufficientFunds_returns422() throws Exception {
        when(accountService.debit(eq(1L), any(BigDecimal.class)))
                .thenThrow(new InsufficientFundsException(1L, new BigDecimal("10.00"), new BigDecimal("40.00")));

        mockMvc.perform(post("/api/accounts/{id}/debit", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmountRequest(new BigDecimal("40.00")))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void postDebit_missingAccount_returns404() throws Exception {
        when(accountService.debit(eq(99L), any(BigDecimal.class)))
                .thenThrow(new AccountNotFoundException(99L));

        mockMvc.perform(post("/api/accounts/{id}/debit", 99L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmountRequest(new BigDecimal("40.00")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void postCredit_valid_returns200WithUpdatedBalance() throws Exception {
        when(accountService.credit(eq(1L), any(BigDecimal.class)))
                .thenReturn(new Account("Bob", "bob@example.com", new BigDecimal("125.50"), "CAD"));

        mockMvc.perform(post("/api/accounts/{id}/credit", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmountRequest(new BigDecimal("25.50")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(125.50));
    }

    @Test
    void postAccount_blankOwnerName_returns400() throws Exception {
        String body = "{\"ownerName\":\"\",\"email\":\"x@example.com\",\"openingBalance\":10.00,\"currency\":\"CAD\"}";
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postDebit_nonPositiveAmount_returns400() throws Exception {
        String body = "{\"amount\":0.00}";
        mockMvc.perform(post("/api/accounts/{id}/debit", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }
}
