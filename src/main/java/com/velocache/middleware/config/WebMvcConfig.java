package com.velocache.middleware.config;
import com.velocache.middleware.interceptor.OpsKeyInterceptor;
import com.velocache.middleware.interceptor.RateLimitInterceptor;
import com.velocache.middleware.interceptor.TrafficTrackingInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;
    @Autowired
    private TrafficTrackingInterceptor trafficTrackingInterceptor;
    @Autowired
    private OpsKeyInterceptor opsKeyInterceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(trafficTrackingInterceptor)
                .excludePathPatterns("/api/health", "/error", "/api/dev/token", "/api/dashboard/**");
        registry.addInterceptor(rateLimitInterceptor)
                .excludePathPatterns("/api/health", "/error", "/api/dev/token");
        registry.addInterceptor(opsKeyInterceptor).addPathPatterns("/api/dashboard/**");
    }
}
