package com.abhedyam.config;

import com.abhedyam.service.FcmService;
import com.abhedyam.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * After any successful change by an owner's phone (POST/PUT/PATCH/DELETE, 2xx), ping the owner's other phones
 * so an open app reloads. Coalesced: one ping per owner+phone per 2 s, however many calls a screen makes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SyncPushInterceptor implements HandlerInterceptor {

    /** Changes the other phones don't need to see. */
    private static final List<String> SKIP = List.of("/api/v1/auth", "/api/v1/fcm", "/api/v1/notifications",
            "/api/v1/feedback", "/api/v1/admin", "/sessions", "/notifications", "/upload", "/images");

    private final FcmService fcmService;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (ex != null || "GET".equals(request.getMethod()) || "OPTIONS".equals(request.getMethod())) return;
        if (response.getStatus() < 200 || response.getStatus() >= 300) return;
        String path = request.getRequestURI();
        if (SKIP.stream().anyMatch(path::contains)) return;
        // only owner tokens carry a session id (customer app / admin / pre-1.13 tokens: skip)
        if (SecurityUtil.getCurrentSessionId() == null) return;
        changed(SecurityUtil.getCurrentUserId(), request.getHeader("X-Device-Id"));
    }

    void changed(UUID ownerId, String fromDevice) {
        String key = ownerId + "|" + (fromDevice == null ? "" : fromDevice);
        if (!pending.add(key)) return;
        timer.schedule(() -> {
            pending.remove(key);
            try {
                fcmService.sendSync(ownerId, fromDevice);
            } catch (Exception e) {
                log.warn("Sync ping failed: {}", e.getMessage());
            }
        }, 2, TimeUnit.SECONDS);
    }
}
