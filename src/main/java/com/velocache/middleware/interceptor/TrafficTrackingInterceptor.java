package com.velocache.middleware.interceptor;

import java.io.IOException;

import com.velocache.middleware.security.JwtUtil;
import com.velocache.middleware.service.TenantTrafficService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TrafficTrackingInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(TrafficTrackingInterceptor.class);

    @Autowired
    private TenantTrafficService trafficService;
    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("Rejecting request: missing or invalid Authorization header");
            return reject(response);
        }

        String token = authHeader.substring(7);
        Claims claims;
        try {
            claims = jwtUtil.validateAndParse(token);
        } catch (Exception e) {
            logger.warn("Rejecting request: JWT parse failure ({})", e.getClass().getName());
            return reject(response);
        }

        String tenantId = claims.get("tenant_id", String.class);
        if (tenantId == null || tenantId.isBlank()) {
            logger.warn("Rejecting request: missing or blank tenant_id claim");
            return reject(response);
        }

        String endpoint = request.getRequestURI();
        request.setAttribute("tenantId", tenantId);
        trafficService.recordRequest(tenantId, endpoint);
        return true;
    }

    private boolean reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Missing or invalid token\"}");
        return false;
    }
}
