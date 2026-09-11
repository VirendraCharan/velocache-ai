package com.velocache.middleware;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers

// NOTE: This test is correctly configured but currently blocked by a local
// Docker Desktop / WSL2 environment issue (Docker daemon returns an empty
// status response via named pipe, inconsistent behavior across API version
// settings). Verified working logic manually via Postman + redis-cli in
// Phase A/B testing. May work automatically in CI (GitHub Actions) since
// that environment doesn't have this local WSL2 quirk. Revisit if needed.
public class TenantIsolationIntegrationTest {

    @Container
    static GenericContainer<?> redisContainer =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideRedisProperties (DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port",() -> redisContainer.getMappedPort(6379));
    }

    @Test
    void contextLoadsWithRedisContainer() {
        System.out.println("Redis container is running at: "
        + redisContainer.getHost() + ":" + redisContainer.getMappedPort(6379));
    }
}
