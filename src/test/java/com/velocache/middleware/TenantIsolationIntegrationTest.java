package com.velocache.middleware;

import com.velocache.middleware.entity.TrafficLog;
import com.velocache.middleware.repository.TrafficLogRepository;
import com.velocache.middleware.security.JwtUtil;
import com.velocache.middleware.service.CacheKeyBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public class TenantIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private TrafficLogRepository trafficLogRepository;

    @Autowired
    private CacheKeyBuilder cacheKeyBuilder;

    private String tenantA;
    private String tenantB;
    private String tokenA;
    private String tokenB;
    private final Set<String> logEndpoints = new HashSet<>();

    @BeforeEach
    void setUp() {
        tenantA = "iso-a-" + UUID.randomUUID();
        tenantB = "iso-b-" + UUID.randomUUID();
        tokenA = jwtUtil.generateToken("test-user", tenantA, List.of("USER"));
        tokenB = jwtUtil.generateToken("test-user", tenantB, List.of("USER"));
        logEndpoints.clear();
    }

    @AfterEach
    void cleanUp() {
        for (String tenantId : List.of(tenantA, tenantB)) {
            Set<String> keys = new HashSet<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match("velocache:*" + tenantId + "*")
                    .count(100)
                    .build();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(cursor.next());
                }
            }
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }

            for (String endpoint : logEndpoints) {
                List<TrafficLog> rows = trafficLogRepository.findByTenantIdAndEndpoint(tenantId, endpoint);
                trafficLogRepository.deleteAll(rows);
            }
        }
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/data").param("endpoint", "/x"))
                .andExpect(result -> assertEquals(401, result.getResponse().getStatus()));

        mockMvc.perform(get("/api/data")
                        .param("endpoint", "/x")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(result -> assertEquals(401, result.getResponse().getStatus()));
    }

    @Test
    void tenantCannotReadAnotherTenantsCache() throws Exception {
        String keyA = cacheKeyBuilder.build(tenantA, "/x");
        stringRedisTemplate.opsForValue().set(keyA, "SECRET-A");

        MvcResult resultA = mockMvc.perform(get("/api/data")
                        .param("endpoint", "/x")
                        .header("Authorization", "Bearer " + tokenA))
                .andReturn();
        assertEquals(200, resultA.getResponse().getStatus());
        assertTrue(resultA.getResponse().getContentAsString().contains("SECRET-A"));

        MvcResult resultB = mockMvc.perform(get("/api/data")
                        .param("endpoint", "/x")
                        .header("Authorization", "Bearer " + tokenB))
                .andReturn();
        assertEquals(200, resultB.getResponse().getStatus());
        assertFalse(resultB.getResponse().getContentAsString().contains("SECRET-A"));
    }

    @Test
    void tenantCannotReadAnotherTenantsHistory() throws Exception {
        String endpoint = "iso-" + UUID.randomUUID();
        logEndpoints.add(endpoint);

        mockMvc.perform(post("/api/track")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content("{\"endpoint\":\"" + endpoint + "\",\"requestCount\":7}"))
                .andExpect(result -> assertEquals(200, result.getResponse().getStatus()));

        MvcResult resultA = mockMvc.perform(get("/api/stats/" + endpoint)
                        .header("Authorization", "Bearer " + tokenA))
                .andReturn();
        assertEquals(200, resultA.getResponse().getStatus());
        assertTrue(resultA.getResponse().getContentAsString().contains(tenantA));

        MvcResult resultB = mockMvc.perform(get("/api/stats/" + endpoint)
                        .header("Authorization", "Bearer " + tokenB))
                .andReturn();
        assertEquals(200, resultB.getResponse().getStatus());
        assertEquals("[]", resultB.getResponse().getContentAsString());
    }

    @Test
    void rateLimitIsPerTenant() throws Exception {
        int allowed = 0;
        int rejected = 0;

        for (int i = 0; i < 105; i++) {
            int status = mockMvc.perform(get("/api/stats/iso-limit")
                            .header("Authorization", "Bearer " + tokenA))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            if (status == 200) {
                allowed++;
            } else if (status == 429) {
                rejected++;
            }
        }

        assertEquals(100, allowed);
        assertEquals(5, rejected);

        mockMvc.perform(get("/api/stats/iso-limit")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(result -> assertEquals(200, result.getResponse().getStatus()));
    }
}
