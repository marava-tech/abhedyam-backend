package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abhedyam.dto.PhoneLoginRequest;
import com.abhedyam.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

/** Regression guard: phone-number-only login must never issue a token again. */
@ExtendWith(MockitoExtension.class)
class AuthServicePhoneLoginTest {
    @InjectMocks AuthService authService; // dependencies stay null: the method must not touch any of them

    @Test
    void phoneOnlyLoginIsRejectedWithoutTouchingAnything() {
        PhoneLoginRequest req = new PhoneLoginRequest();
        req.setPhone("+919876543210");
        BusinessException e = assertThrows(BusinessException.class, () -> authService.loginWithPhone(req));
        assertEquals("OTP_REQUIRED", e.getErrorCode());
    }
}
