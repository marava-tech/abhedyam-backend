package com.abhedyam.config;

import static org.junit.jupiter.api.Assertions.*;

import com.abhedyam.exception.BusinessException;
import com.abhedyam.security.UserPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** What the owner, a family member and a helper may do. */
class RoleGuardInterceptorTest {

    private final RoleGuardInterceptor guard = new RoleGuardInterceptor();
    private final String shop = "/api/v1/owners/" + UUID.randomUUID();

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    private void as(String role) {
        UserPrincipal p = new UserPrincipal(UUID.randomUUID(), "x", UUID.randomUUID(), role == null ? null : UUID.randomUUID(), role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, List.of()));
    }

    private boolean allowed(String method, String path) {
        try {
            return guard.preHandle(new MockHttpServletRequest(method, path), null, null);
        } catch (BusinessException e) {
            assertEquals("NOT_ALLOWED", e.getErrorCode());
            return false;
        }
    }

    @Test
    void owner_CanDoEverything() {
        as(null);
        assertTrue(allowed("DELETE", "/api/v1/owner/account"));
        assertTrue(allowed("POST", shop + "/members"));
        assertTrue(allowed("PATCH", shop + "/settings"));
    }

    @Test
    void family_EverythingButShopLevelThings() {
        as("FAMILY");
        assertTrue(allowed("POST", "/api/v1/sales"));
        assertTrue(allowed("PUT", "/api/v1/customers/" + UUID.randomUUID()));
        assertTrue(allowed("POST", shop + "/payments/write-off"));
        assertTrue(allowed("POST", shop + "/sessions/logout"));
        assertTrue(allowed("GET", "/api/v1/owner/account/deletion")); // Home banner
        assertTrue(allowed("POST", shop + "/settings/chase-contacted"));
        assertFalse(allowed("PATCH", shop + "/settings"));
        assertFalse(allowed("PATCH", shop));
        assertFalse(allowed("GET", shop + "/members"));
        assertFalse(allowed("POST", shop + "/members"));
        assertFalse(allowed("GET", shop + "/sessions"));
        assertFalse(allowed("DELETE", "/api/v1/owner/account"));
        assertFalse(allowed("POST", "/api/v1/owner/account/export-request"));
        assertFalse(allowed("POST", shop + "/bulk-import"));
        assertFalse(allowed("PATCH", "/api/v1/upi-accounts/owner/" + UUID.randomUUID()));
    }

    @Test
    void helper_OnlyAdds() {
        as("HELPER");
        assertTrue(allowed("POST", "/api/v1/sales"));
        assertTrue(allowed("POST", shop + "/payments/collect"));
        assertTrue(allowed("POST", "/api/v1/customers"));
        assertTrue(allowed("POST", shop + "/customers/" + UUID.randomUUID() + "/dues"));
        assertTrue(allowed("POST", shop + "/settings/chase-contacted"));
        assertTrue(allowed("POST", "/api/v1/fcm/register"));
        assertTrue(allowed("PATCH", "/api/v1/notifications/" + UUID.randomUUID() + "/read"));
        assertTrue(allowed("GET", shop + "/customers"));
        assertFalse(allowed("PUT", "/api/v1/customers/" + UUID.randomUUID()));
        assertFalse(allowed("POST", shop + "/payments/write-off"));
        assertFalse(allowed("DELETE", shop + "/customers/" + UUID.randomUUID() + "/dues/" + UUID.randomUUID()));
        assertFalse(allowed("POST", "/api/v1/sales/transaction/abc/cancel"));
        assertFalse(allowed("POST", shop + "/items"));
        assertFalse(allowed("POST", shop + "/purchases"));
        assertFalse(allowed("PUT", shop + "/customers/" + UUID.randomUUID() + "/chase"));
        assertFalse(allowed("GET", shop + "/members"));
    }
}
