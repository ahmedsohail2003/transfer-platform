package com.ahmedsohail.transferservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies the Spring application context starts and all beans wire together. The
 * account-service is not contacted here — only the wiring (controllers, service, client
 * bean, JPA) is checked.
 */
@SpringBootTest
class TransferServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
