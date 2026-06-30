package com.ahmedsohail.accountservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test: boots the whole application against the embedded H2
 * database and drives it through real HTTP requests end to end.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
        // Verifies the Spring application context starts and all beans wire together.
    }

    @Test
    void endToEnd_openDebitCreditThenCheckBalance() throws Exception {
        Long id = createAccount("Alice", "alice@example.com", "100.00", "CAD");

        // Debit 40 -> balance 60
        mockMvc.perform(post("/api/accounts/{id}/debit", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 40.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(60.00));

        // Credit 15.50 -> balance 75.50
        mockMvc.perform(post("/api/accounts/{id}/credit", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 15.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(75.50));

        // Balance endpoint reflects the running total
        mockMvc.perform(get("/api/accounts/{id}/balance", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(id))
                .andExpect(jsonPath("$.balance").value(75.50))
                .andExpect(jsonPath("$.currency").value("CAD"));
    }

    @Test
    void debit_beyondBalance_returns422AndLeavesBalanceUntouched() throws Exception {
        Long id = createAccount("Bob", "bob@example.com", "20.00", "CAD");

        mockMvc.perform(post("/api/accounts/{id}/debit", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 50.00}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));

        // The failed debit did not change the balance.
        mockMvc.perform(get("/api/accounts/{id}/balance", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(20.00));
    }

    private Long createAccount(String name, String email, String balance, String currency) throws Exception {
        String body = """
                {"ownerName": "%s", "email": "%s", "openingBalance": %s, "currency": "%s"}
                """.formatted(name, email, balance, currency);
        MvcResult result = mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
