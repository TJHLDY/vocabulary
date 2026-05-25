package org.gduf.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int LOGIN_MAX = 10;
    private static final int REGISTER_MAX = 5;
    private static final long WINDOW_MS = 60_000;

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        int max;
        if (path.equals("/api/auth/login")) {
            max = LOGIN_MAX;
        } else if (path.equals("/api/auth/register")) {
            max = REGISTER_MAX;
        } else {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = request.getRemoteAddr();
        String key = ip + ":" + path;
        long now = System.currentTimeMillis();

        WindowCounter wc = counters.compute(key, (k, v) -> {
            if (v == null || now - v.windowStart > WINDOW_MS) {
                return new WindowCounter(now);
            }
            return v;
        });

        synchronized (wc) {
            if (now - wc.windowStart > WINDOW_MS) {
                wc.windowStart = now;
                wc.count = 0;
            }
            wc.count++;
            if (wc.count > max) {
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(429);
                response.getWriter().write("{\"code\":429,\"msg\":\"请求过于频繁，请稍后再试\",\"data\":null}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static class WindowCounter {
        long windowStart;
        int count;

        WindowCounter(long windowStart) {
            this.windowStart = windowStart;
            this.count = 0;
        }
    }
}
