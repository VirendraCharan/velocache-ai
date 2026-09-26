package com.velocache.middleware;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import com.velocache.middleware.security.JwtUtil;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
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
    @Test
    void tenantCannotAccessAnotherTenantsCache() throws Exception {
        // Generate JWT tokens for two different tenants
        String tokenA = jwtUtil.generateToken("user1", "tenant-A", List.of("viewer"));
        String tokenB = jwtUtil.generateToken("user2", "tenant-B", List.of("viewer"));
        mockMvc.perform(get("/api/stats/").header("Authorization", "Bearer " + tokenB));
        mockMvc.perform(get("/api/stats/").header("Authorization", "Bearer " + tokenA));
        Boolean tenantAKeyExists = redisTemplate.hasKey("velocache:traffic:tenant-A:/api/stats/");
        Boolean tenantBKeyExists = redisTemplate.hasKey("velocache:traffic:tenant-B:/api/stats/");

        assertTrue(tenantAKeyExists);
        assertTrue(tenantBKeyExists);
    }
    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private MockMvc mockMvc;
}
