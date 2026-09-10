package com.abhedyam.service.interfaces;

public interface ISmsService {
    void sendSms(String phone, String message);
    boolean sendSmsWithRetry(String phone, String message, int maxRetries);
    void sendOtp(String phone, String otp);
}

