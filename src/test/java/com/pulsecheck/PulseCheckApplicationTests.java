package com.pulsecheck;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "pulsecheck.monitoring.enabled=false")
class PulseCheckApplicationTests {

    @Test
    void contextLoads() {
    }
}
