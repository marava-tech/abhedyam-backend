package com.abhedyam.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** SecurityConfig lets only isAdmin() principals into /api/v1/admin/** and /actuator/**. */
class UserPrincipalTest {
    @Test
    void onlyTheTotpAdminIsAdmin() {
        assertTrue(new UserPrincipal(UserPrincipal.ADMIN_ID, "ADMIN").isAdmin());
        assertFalse(new UserPrincipal(UUID.randomUUID(), "+919876543210").isAdmin());
        assertFalse(new UserPrincipal(UUID.randomUUID(), "ADMIN").isAdmin());
        assertFalse(new UserPrincipal(UserPrincipal.ADMIN_ID, "+919876543210").isAdmin());
    }
}
