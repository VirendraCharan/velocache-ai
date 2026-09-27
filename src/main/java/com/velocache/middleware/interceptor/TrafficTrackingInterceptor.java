package com.velocache.middleware.interceptor;

import com.velocache.middleware.security.JwtUtil;
import com.velocache.middleware.service.TenantTrafficService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TrafficTrackingInterceptor implements HandlerInterceptor {

    @Autowired
    private TenantTrafficService trafficService;
    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authHeader = request.getHeader("Authorization");
        String tenantId = "unknown-tenant";
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = jwtUtil.validateAndParse(token);
                tenantId = claims.get("tenantId", String.class);
            } catch (Exception e) {
                System.err.println("Invalid or expired JWT: " + e.getMessage());
            }
        }
        String endpoint = request.getRequestURI();
        trafficService.recordRequest(tenantId, endpoint);
        return true;
    }
}
