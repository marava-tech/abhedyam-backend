package com.abhedyam.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal implements UserDetails {
    
    private UUID userId;
    private String phone;
    /** This phone's session (JWT "sid"); null for old tokens. */
    private UUID sessionId;
    /** Family member / helper signed in on this phone; null = the owner. */
    private UUID memberId;
    /** FAMILY / HELPER; null = the owner. */
    private String role;

    public UserPrincipal(UUID userId, String phone) {
        this(userId, phone, null, null, null);
    }

    public UserPrincipal(UUID userId, String phone, UUID sessionId) {
        this(userId, phone, sessionId, null, null);
    }
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }
    
    @Override
    public String getPassword() {
        return null;
    }
    
    @Override
    public String getUsername() {
        return phone;
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }
    
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
}

