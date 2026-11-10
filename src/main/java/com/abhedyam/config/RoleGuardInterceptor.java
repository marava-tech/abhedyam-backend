package com.abhedyam.config;

import com.abhedyam.exception.BusinessException;
import com.abhedyam.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * What family members and helpers may do (the owner's token has no role: everything allowed).
 * Family: everything except the owner-only list. Helper: only adds (sale, payment, customer, due, notes) — no edits or deletes.
 * Refused → 403 NOT_ALLOWED. ponytail: helper GETs are not blocked (totals hidden in the app); block them if helpers
 * outside the family appear.
 */
@Component
public class RoleGuardInterceptor implements HandlerInterceptor {

    private static final AntPathMatcher PATHS = new AntPathMatcher();

    /** "METHOD path" ("*" = any method). Shop-level things only the owner changes. */
    static final List<String> OWNER_ONLY = List.of(
            "PATCH /api/v1/owners/*",
            "PATCH /api/v1/owners/*/settings",
            "PUT /api/v1/owners/*/notification-settings",
            "POST /api/v1/upi-accounts/**", "PATCH /api/v1/upi-accounts/**", "PUT /api/v1/upi-accounts/**", "DELETE /api/v1/upi-accounts/**",
            "* /api/v1/owners/*/members", "* /api/v1/owners/*/members/**",
            "GET /api/v1/owners/*/sessions", "DELETE /api/v1/owners/*/sessions/*", "POST /api/v1/owners/*/sessions/logout-others",
            "DELETE /api/v1/owner/account", "POST /api/v1/owner/account/**",
            "POST /api/v1/referrals/apply",
            "POST /api/v1/owners/*/bulk-import",
            "PATCH /api/v1/location-details/users/*",
            "PATCH /api/v1/users/*", "POST /api/v1/users");

    /** The only changes a helper may make: adding, never editing or deleting. */
    static final List<String> HELPER_WRITES = List.of(
            "POST /api/v1/sales",
            "POST /api/v1/owners/*/payments/collect",
            "POST /api/v1/customers",
            "POST /api/v1/owners/*/customers/*/dues",
            "POST /api/v1/notes", "POST /api/v1/reminders",
            "POST /api/v1/owners/*/settings/chase-contacted",
            "POST /api/v1/files/upload",
            "* /api/v1/fcm/**",
            "POST /api/v1/notifications/**", "PATCH /api/v1/notifications/**",
            "POST /api/v1/owners/*/sessions/logout",
            "POST /api/v1/feedbacks",
            "POST /api/v1/stats/analytics",
            "POST /api/v1/customers/nearest", "POST /api/v1/location-details/customers/locations");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String role = SecurityUtil.getCurrentRole();
        if (role == null) return true; // the owner (or no login: public endpoints)
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("OPTIONS".equals(method)) return true;
        if (matches(OWNER_ONLY, method, path)) {
            throw new BusinessException("NOT_ALLOWED", "Only the shop owner can do this");
        }
        if ("HELPER".equals(role) && !"GET".equals(method) && !matches(HELPER_WRITES, method, path)) {
            throw new BusinessException("NOT_ALLOWED", "Helpers can add sales, payments and customers. Ask the owner to change or delete entries");
        }
        return true;
    }

    static boolean matches(List<String> rules, String method, String path) {
        for (String rule : rules) {
            int space = rule.indexOf(' ');
            String m = rule.substring(0, space);
            if (("*".equals(m) || m.equals(method)) && PATHS.match(rule.substring(space + 1), path)) return true;
        }
        return false;
    }
}
