package com.velocache.middleware.security;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    private final SecretKey secretKey;

    private static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    public JwtUtil(@Value("${velocache.jwt-secret:}") String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            this.secretKey = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
            logger.warn("A random signing key is in use and tokens become invalid when the app restarts");
            return;
        }

        byte[] bytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("velocache.jwt-secret must be at least 32 bytes long");
        }
        this.secretKey = Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(String userId, String tenantId, List<String> roles) {
        return Jwts.builder()
                .subject(userId)
                .claim("tenant_id", tenantId)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(secretKey)
                .compact();
    }

    public Claims validateAndParse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
