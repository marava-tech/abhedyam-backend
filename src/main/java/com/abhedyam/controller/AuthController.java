package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.AdminLoginRequest;
import com.abhedyam.dto.AuthResponse;
import com.abhedyam.dto.GoogleLoginRequest;
import com.abhedyam.dto.PhoneLoginRequest;
import com.abhedyam.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/google/login")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-Device-Name", required = false) String deviceName) {
        AuthResponse response = authService.loginWithGoogle(request, deviceId, deviceName);
        return ApiResponse.success(response);
    }

    @PostMapping("/google/join")
    @ResponseStatus(HttpStatus.OK)
    @io.swagger.v3.oas.annotations.Operation(summary = "Join a shop from an invite (family member / helper)",
            description = "Google sign-in + invite code. One Gmail = one shop. Returns a token for the shop (userId = owner id) with memberId/memberRole.")
    public ApiResponse<AuthResponse> joinShop(@Valid @RequestBody com.abhedyam.dto.JoinShopRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-Device-Name", required = false) String deviceName) {
        return ApiResponse.success(authService.joinShop(request, deviceId, deviceName));
    }

    @PostMapping("/phone/login")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<AuthResponse> loginWithPhone(@Valid @RequestBody PhoneLoginRequest request) {
        AuthResponse response = authService.loginWithPhone(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/admin/login")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<AuthResponse> loginAdmin(@Valid @RequestBody AdminLoginRequest request) {
        AuthResponse response = authService.adminLogin(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<AuthResponse> refresh(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-Device-Name", required = false) String deviceName) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new com.abhedyam.exception.BusinessException("MISSING_TOKEN", "Authorization bearer token is required");
        }
        return ApiResponse.success(authService.refresh(authorization.substring(7), deviceId, deviceName));
    }
}
