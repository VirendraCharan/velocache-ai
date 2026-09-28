package com.velocache.middleware.controller;

import com.velocache.middleware.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DevTokenController {

    @Value("${velocache.dev-token-secret}")
    private String devTokenSecret;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/api/dev/token")
    public ResponseEntity<String> issueToken(
            @RequestHeader(value = "X-Dev-Secret", required = false) String secret,
            @RequestParam String tenantId) {
        if (!devTokenSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Wrong knock");
        }
        String token = jwtUtil.generateToken("sim-user-" + tenantId, tenantId , List.of("USER"));
        return ResponseEntity.ok(token);
    }
}
