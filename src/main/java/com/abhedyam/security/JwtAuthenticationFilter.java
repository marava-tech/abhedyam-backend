package com.abhedyam.security;

import com.abhedyam.dto.ErrorResponse;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Autowired
    private ApplicationContext applicationContext;

    private OwnerRepository ownerRepository;
    private CustomerRepository customerRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    private OwnerRepository getOwnerRepository() {
        if (ownerRepository == null) {
            ownerRepository = applicationContext.getBean(OwnerRepository.class);
        }
        return ownerRepository;
    }

    private CustomerRepository getCustomerRepository() {
        if (customerRepository == null) {
            customerRepository = applicationContext.getBean(CustomerRepository.class);
        }
        return customerRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip if already authenticated by AdminKeyFilter
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            log.debug("Already authenticated, skipping JWT validation for path: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        // Skip JWT validation for public endpoints. /public/** is permitAll so a
        // pay-link visitor (or an owner previewing their own link) must not be
        // rejected just because a stale Bearer token was forwarded.
        if (path.startsWith("/api/v1/auth") || path.startsWith("/api/v1/health")
                || path.startsWith("/swagger-ui") || path.startsWith("/api/v1/public")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            if (!jwtUtil.validateToken(token)) {
                sendUnauthorizedError(response, "Invalid or expired token", "INVALID_TOKEN", request.getRequestURI());
                return;
            }

            UUID userId = jwtUtil.getUserIdFromToken(token);
            String phone = jwtUtil.getPhoneFromToken(token);

            if ("ADMIN".equals(phone)) {
                // Admin user, skip DB check
                log.debug("Admin user authenticated via JWT");
            } else {
                try {
                    boolean userExists = getOwnerRepository().existsById(userId)
                            || getCustomerRepository().existsById(userId);
                    if (!userExists) {
                        log.warn("User not found for token - userId: {}", userId);
                        sendUnauthorizedError(response, "User account not found or has been deleted", "USER_NOT_FOUND",
                                request.getRequestURI());
                        return;
                    }
                } catch (Exception e) {
                    log.error("Error checking user existence: {}", e.getMessage());
                }
            }

            UserPrincipal userPrincipal = new UserPrincipal(userId, phone);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userPrincipal,
                    null,
                    new ArrayList<>());

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("JWT token validated and authentication set for userId: {}", userId);

        } catch (Exception e) {
            log.error("Error validating JWT token: {}", e.getMessage());
            sendUnauthorizedError(response, "Token validation failed", "TOKEN_VALIDATION_ERROR",
                    request.getRequestURI());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorizedError(HttpServletResponse response, String message, String errorCode, String path)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = new ErrorResponse(
                "Unauthorized",
                message,
                errorCode,
                null,
                path);

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
