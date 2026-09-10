package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.AdminLoginRequest;
import com.abhedyam.dto.AuthResponse;
import com.abhedyam.dto.GoogleLoginRequest;
import com.abhedyam.dto.OtpSendRequest;
import com.abhedyam.dto.OtpVerifyRequest;
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
    public ApiResponse<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        AuthResponse response = authService.loginWithGoogle(request);
        return ApiResponse.success(response);
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

    @PostMapping("/otp/send")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> sendOtp(@Valid @RequestBody OtpSendRequest request) {
        authService.sendCustomerOtp(request.getPhone());
        return ApiResponse.success(null);
    }

    @PostMapping("/otp/verify")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<AuthResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return ApiResponse.success(authService.verifyCustomerOtp(request.getPhone(), request.getOtp()));
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<AuthResponse> refresh(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new com.abhedyam.exception.BusinessException("MISSING_TOKEN", "Authorization bearer token is required");
        }
        return ApiResponse.success(authService.refresh(authorization.substring(7)));
    }
}
