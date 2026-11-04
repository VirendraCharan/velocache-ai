package com.velocache.middleware.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class OpsKeyInterceptor implements HandlerInterceptor {

    @Value("${velocache.ops-key:}")
    private String opsKey;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        String supplied = request.getHeader("X-Ops-Key");
        boolean ok = !opsKey.isEmpty()
                && supplied != null
                && MessageDigest.isEqual(
                opsKey.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            response.setStatus(401);
            return false;
        }
        return true;
    }
}
