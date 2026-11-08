package com.abhedyam.util;

import com.abhedyam.exception.BusinessException;
import com.abhedyam.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public class SecurityUtil {
    
    /** Family member / helper making this request; null = the owner (or no request, e.g. a scheduled job). */
    public static UUID getCurrentMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal p) return p.getMemberId();
        return null;
    }

    /** FAMILY / HELPER; null = the owner. */
    public static String getCurrentRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal p) return p.getRole();
        return null;
    }

    /** This phone's session (JWT "sid"); null for old tokens. */
    public static UUID getCurrentSessionId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal p) return p.getSessionId();
        return null;
    }

    public static UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            return userPrincipal.getUserId();
        }
        throw new BusinessException("UNAUTHORIZED", "User not authenticated. Please provide a valid token.");
    }

    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() 
            && authentication.getPrincipal() instanceof UserPrincipal;
    }
}

