package com.guesschess;

import com.guesschess.support.PostgresTestContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Import(PostgresTestContainerConfig.class)
@ActiveProfiles("test")
class GuesschessApplicationTests {

    @Test
    void contextLoads() {
    }
}
