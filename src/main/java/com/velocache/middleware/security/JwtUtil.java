package com.velocache.middleware.security;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

@Component
public class JwtUtil {

    private final SecretKey secretKey = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);

    private static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

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
