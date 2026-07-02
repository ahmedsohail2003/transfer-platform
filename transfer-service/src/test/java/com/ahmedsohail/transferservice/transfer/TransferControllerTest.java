package com.ahmedsohail.transferservice.transfer;

import com.ahmedsohail.transferservice.common.AccountNotFoundException;
import com.ahmedsohail.transferservice.common.InsufficientFundsException;
import com.ahmedsohail.transferservice.common.TransferFailedException;
import com.ahmedsohail.transferservice.common.TransferNotFoundException;
import com.ahmedsohail.transferservice.transfer.dto.TransferRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link TransferController}. The service is mocked, so these verify
 * HTTP wiring only: status codes, request validation, and exception-to-status mapping.
 */
@WebMvcTest(TransferController.class)
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransferService transferService;

    @Test
    void postTransfer_validRequest_returns201() throws Exception {
        Transfer saved = new Transfer(1L, 2L, new BigDecimal("30.00"), "CAD", TransferStatus.COMPLETED, "rent");
        when(transferService.transfer(any(TransferRequest.class))).thenReturn(saved);

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransferRequest(1L, 2L, new BigDecimal("30.00"), "rent"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(30.00))
                .andExpect(jsonPath("$.currency").value("CAD"));
    }

    @Test
    void postTransfer_zeroAmount_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransferRequest(1L, 2L, new BigDecimal("0.00"), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.amount").value("amount must be greater than zero"));
    }

    @Test
    void postTransfer_subCentAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransferRequest(1L, 2L, new BigDecimal("10.999"), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").value("amount must have at most 2 decimal places"));
    }

    @Test
    void postTransfer_nullFromAccount_returns400() throws Exception {
        String body = "{\"toAccountId\":2,\"amount\":10.00}";
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fromAccountId").exists());
    }

    @Test
    void postTransfer_insufficientFunds_returns422() throws Exception {
        when(transferService.transfer(any(TransferRequest.class)))
                .thenThrow(new InsufficientFundsException(1L));

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransferRequest(1L, 2L, new BigDecimal("30.00"), null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void postTransfer_unknownAccount_returns404() throws Exception {
        when(transferService.transfer(any(TransferRequest.class)))
                .thenThrow(new AccountNotFoundException(99L));

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransferRequest(99L, 2L, new BigDecimal("30.00"), null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void postTransfer_whenDownstreamCreditFails_returns502() throws Exception {
        when(transferService.transfer(any(TransferRequest.class)))
                .thenThrow(new TransferFailedException("Could not credit account 2; sender was refunded"));

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransferRequest(1L, 2L, new BigDecimal("30.00"), null))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }

    @Test
    void getTransfer_returns200() throws Exception {
        Transfer saved = new Transfer(1L, 2L, new BigDecimal("30.00"), "CAD", TransferStatus.COMPLETED, "rent");
        when(transferService.getTransfer(5L)).thenReturn(saved);

        mockMvc.perform(get("/api/transfers/{id}", 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(30.00));
    }

    @Test
    void getTransfer_whenMissing_returns404() throws Exception {
        when(transferService.getTransfer(99L)).thenThrow(new TransferNotFoundException(99L));

        mockMvc.perform(get("/api/transfers/{id}", 99L))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAccountHistory_returns200WithArray() throws Exception {
        Transfer saved = new Transfer(1L, 2L, new BigDecimal("30.00"), "CAD", TransferStatus.COMPLETED, "rent");
        when(transferService.historyFor(1L)).thenReturn(List.of(saved));

        mockMvc.perform(get("/api/accounts/{id}/transfers", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }
}
