package com.velocache.middleware.interceptor;

import com.velocache.middleware.service.TenantTrafficService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TrafficTrackingInterceptor implements HandlerInterceptor {

    @Autowired
    private TenantTrafficService trafficService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenantId = "tenant-a"; // hardcoded on purpose for now
        String endpoint = request.getRequestURI();
        trafficService.recordRequest(tenantId, endpoint);
        return true;
    }
}
