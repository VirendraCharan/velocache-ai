package com.velocache.middleware.interceptor;
import com.velocache.middleware.dto.ErrorResponseDto;
import com.velocache.middleware.service.RedisSlidingWindowService;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;


@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Autowired
    private RedisSlidingWindowService slidingWindowService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String endpoint = request.getRequestURI();
        request.setAttribute("endpoint", endpoint);

        Object tenantAttribute = request.getAttribute("tenantId");
        String identity;
        if (tenantAttribute instanceof String tenantId && !tenantId.isBlank()) {
            identity = tenantId;
        } else {
            identity = "ip:" + request.getRemoteAddr();
        }

        boolean allowed = slidingWindowService.isAllowed(identity, endpoint);

        if(!allowed) {
            response.setStatus(429);
            response.setContentType("application/json");
            ErrorResponseDto error = new ErrorResponseDto("Rate limit exceeded", 60);
            String json = JSON_MAPPER.writeValueAsString(error);
            response.getWriter().write(json);
            return false;
        }
        return true;
    }
}
