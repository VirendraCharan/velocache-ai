package com.velocache.middleware.config;
import com.velocache.middleware.interceptor.OpsKeyInterceptor;
import com.velocache.middleware.interceptor.RateLimitInterceptor;
import com.velocache.middleware.interceptor.TrafficTrackingInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
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
    @Value("${velocache.cors.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(trafficTrackingInterceptor)
                .excludePathPatterns("/api/health", "/error", "/api/dev/token", "/api/dashboard/**");
        registry.addInterceptor(rateLimitInterceptor)
                .excludePathPatterns("/api/health", "/error", "/api/dev/token");
        registry.addInterceptor(opsKeyInterceptor).addPathPatterns("/api/dashboard/**");
    }
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/dashboard/**")
                .allowedOrigins(allowedOrigin)
                .allowedMethods("GET")
                .allowedHeaders("X-Ops-Key")
                .maxAge(3600);
    }
}
