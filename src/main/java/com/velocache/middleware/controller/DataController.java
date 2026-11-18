package com.velocache.middleware.controller;

import com.velocache.middleware.service.CacheAsideService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DataController {
    @Autowired
    private CacheAsideService cacheAsideService;

    @GetMapping("/api/data")
    public String getData(@RequestParam String endpoint, HttpServletRequest request) {
        String tenantId = (String) request.getAttribute("tenantId");
        return cacheAsideService.getOrLoad(tenantId, endpoint);
    }
}
