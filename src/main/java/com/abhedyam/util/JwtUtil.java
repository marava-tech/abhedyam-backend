package com.abhedyam.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Component
@lombok.extern.slf4j.Slf4j
public class JwtUtil {
    
    @Value("${app.jwt.secret}")
    private String secret;
    
    @Value("${app.jwt.expiration:1296000000}")
    private Long expiration;
    
    @Value("${app.jwt.customer-expiration:2592000000}")
    private Long customerExpiration;
    
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    
    public String generateToken(UUID userId, String phone) {
        return generateToken(userId, phone, null);
    }

    /** [sessionId]: the owner_sessions row of this phone (claim "sid"), so the phone can be logged out from another one. */
    public String generateToken(UUID userId, String phone, UUID sessionId) {
        return generateToken(userId, phone, sessionId, null, null);
    }

    /** A family member / helper: sub stays the shop owner's id; [memberId] (claim "mid") and [role] say who it is. */
    public String generateToken(UUID userId, String phone, UUID sessionId, UUID memberId, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        
        return Jwts.builder()
                .subject(userId.toString())
                .claim("phone", phone)
                .claim("sid", sessionId == null ? null : sessionId.toString())
                .claim("mid", memberId == null ? null : memberId.toString())
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }
    
    public String generateCustomerToken(UUID userId, String phone) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + customerExpiration);
        
        return Jwts.builder()
                .subject(userId.toString())
                .claim("phone", phone)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }
    
    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(getClaimFromToken(token, Claims::getSubject));
    }
    
    /** Family member / helper id (claim "mid"); null = the owner. */
    public UUID getMemberIdFromToken(String token) {
        String mid = getClaimFromToken(token, claims -> claims.get("mid", String.class));
        return mid == null ? null : UUID.fromString(mid);
    }

    /** FAMILY / HELPER; null = the owner. */
    public String getRoleFromToken(String token) {
        return getClaimFromToken(token, claims -> claims.get("role", String.class));
    }

    /** Null for tokens made before sessions existed (and for customer/admin tokens). */
    public UUID getSessionIdFromToken(String token) {
        String sid = getClaimFromToken(token, claims -> claims.get("sid", String.class));
        return sid == null ? null : UUID.fromString(sid);
    }

    public String getPhoneFromToken(String token) {
        return getClaimFromToken(token, claims -> claims.get("phone", String.class));
    }
    
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }
    
    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }
    
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    public Boolean isTokenExpired(String token) {
        try {
            final Date expiration = getExpirationDateFromToken(token);
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
    
    public Boolean validateToken(String token) {
        try {
            getAllClaimsFromToken(token);
            return !isTokenExpired(token);
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return false; // normal: the app renews and retries
        } catch (Exception e) {
            // Logged: the first requests after a restart were rejected here with no trace (2026-11-08).
            log.warn("JWT rejected: {}: {}", e.getClass().getName(), e.getMessage());
            return false;
        }
    }
}

