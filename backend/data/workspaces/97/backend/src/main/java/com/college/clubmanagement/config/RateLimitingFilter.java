package com.college.clubmanagement.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter implements Filter {

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String key = getClientKey(httpRequest);
        Bucket bucket = resolveBucket(key, httpRequest);

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                    "{\"error\": \"Too many requests. Please try again later.\"}");
        }
    }

    private Bucket resolveBucket(String key, HttpServletRequest request) {
        return cache.computeIfAbsent(key, k -> createBucket(request));
    }

    private Bucket createBucket(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Stricter limits for authentication endpoints
        if (path.contains("/auth/login")) {
            // 5 attempts per 15 minutes
            Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(15)));
            return Bucket.builder().addLimit(limit).build();
        } else if (path.contains("/auth/register")) {
            // 3 attempts per hour
            Bandwidth limit = Bandwidth.classic(3, Refill.intervally(3, Duration.ofHours(1)));
            return Bucket.builder().addLimit(limit).build();
        } else {
            // General API: 100 requests per minute
            Bandwidth limit = Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1)));
            return Bucket.builder().addLimit(limit).build();
        }
    }

    private String getClientKey(HttpServletRequest request) {
        // Use IP address as key (in production, consider using user ID for
        // authenticated requests)
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddr();
        }
        return clientIp + ":" + request.getRequestURI();
    }
}
