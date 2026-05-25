package org.gduf.security;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.gduf.common.JwtUtils;
import org.gduf.entity.User;
import org.gduf.mapper.UserMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    public static final ConcurrentHashMap<Long, Long> LAST_ACTIVE = new ConcurrentHashMap<>();
    private static final long THREE_DAYS_MS = 3L * 24 * 60 * 60 * 1000;
    private static final long DB_WRITE_INTERVAL_MS = 5L * 60 * 1000;

    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;
    private final ConcurrentHashMap<Long, Long> lastDbWrite = new ConcurrentHashMap<>();

    public JwtAuthFilter(JwtUtils jwtUtils, UserMapper userMapper) {
        this.jwtUtils = jwtUtils;
        this.userMapper = userMapper;
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void cleanStaleEntries() {
        long now = System.currentTimeMillis();
        long threshold = now - THREE_DAYS_MS;
        cleanMap(LAST_ACTIVE, threshold);
        cleanMap(lastDbWrite, now - DB_WRITE_INTERVAL_MS * 2);
    }

    private void cleanMap(Map<Long, Long> map, long threshold) {
        Iterator<Map.Entry<Long, Long>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < threshold) {
                it.remove();
            }
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/") || path.startsWith("/api/words/dict-proxy/") || !path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"msg\":\"未登录\",\"data\":null}");
            return;
        }

        try {
            String token = header.substring(7);
            Claims claims = jwtUtils.parse(token);
            Long userId = Long.valueOf(claims.getSubject());
            String role = claims.get("role", String.class);
            if (role == null) role = "user";
            boolean remember = Boolean.TRUE.equals(claims.get("remember"));
            long now = System.currentTimeMillis();

            if (remember) {
                try {
                    checkRememberActivity(userId, now);
                } catch (RuntimeException e) {
                    if (e.getMessage() != null && e.getMessage().contains("3天未活跃")) {
                        throw e;
                    }
                    log.warn("Remember-me DB check failed for userId={}", userId, e);
                }
            }

            LAST_ACTIVE.put(userId, now);

            if (remember) {
                try {
                    flushLastActive(userId, now);
                } catch (Exception e) {
                    log.warn("Failed to flush last_active_at for userId={}", userId, e);
                }
            }

            UserContext.setUserId(userId);
            UserContext.setRole(role);

            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception e) {
            log.error("JWT auth failed for path={}", request.getRequestURI(), e);
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            String msg = e.getMessage() != null && e.getMessage().contains("3天未活跃")
                    ? "{\"code\":401,\"msg\":\"3天未活跃，请重新登录\",\"data\":null}"
                    : "{\"code\":401,\"msg\":\"token无效\",\"data\":null}";
            response.getWriter().write(msg);
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void checkRememberActivity(Long userId, long now) {
        Long lastActive = LAST_ACTIVE.get(userId);
        if (lastActive != null) {
            if (now - lastActive > THREE_DAYS_MS) {
                User user = userMapper.selectById(userId);
                long dbLast = (user != null && user.getLastActiveAt() != null)
                        ? user.getLastActiveAt().getTime() : 0L;
                if (dbLast == 0L || now - dbLast > THREE_DAYS_MS) {
                    throw new RuntimeException("3天未活跃，请重新登录");
                }
                LAST_ACTIVE.put(userId, dbLast);
            }
        } else {
            User user = userMapper.selectById(userId);
            long dbLast = (user != null && user.getLastActiveAt() != null)
                    ? user.getLastActiveAt().getTime() : 0L;
            if (dbLast > 0L && now - dbLast > THREE_DAYS_MS) {
                throw new RuntimeException("3天未活跃，请重新登录");
            }
            LAST_ACTIVE.put(userId, dbLast > 0L ? dbLast : now);
        }
    }

    private void flushLastActive(Long userId, long now) {
        Long lastWrite = lastDbWrite.get(userId);
        if (lastWrite == null || now - lastWrite > DB_WRITE_INTERVAL_MS) {
            UpdateWrapper<User> uw = new UpdateWrapper<>();
            uw.eq("id", userId).set("last_active_at", new Date(now));
            userMapper.update(null, uw);
            lastDbWrite.put(userId, now);
        }
    }
}
