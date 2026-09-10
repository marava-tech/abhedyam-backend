package com.abhedyam.service;

import com.abhedyam.service.interfaces.ISmsService;
import com.abhedyam.util.PhoneUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsService implements ISmsService {
    
    private final RestTemplate restTemplate;
    
    @Value("${app.twofactor.api-key:3f77c89e-c18d-11f0-a6b2-0200cd936042}")
    private String apiKey;
    
    @Value("${app.twofactor.otp-template:Abhedyam_OTP_Template}")
    private String otpTemplateName;
    
    private static final String TWO_FACTOR_BASE_URL = "https://2factor.in/API/V1";
    
    @Override
    public void sendOtp(String phone, String otp) {
        sendOtpWithTemplate(phone, otp);
    }
    
    private boolean sendOtpWithTemplate(String phone, String otp) {
        if (phone == null || phone.isEmpty()) {
            log.info("Phone number not provided. Skipping SMS OTP send. Email is primary channel.");
            return false;
        }
        
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("2Factor API key not configured. Skipping OTP send.");
            return false;
        }
        
        if (otp == null || otp.length() < 4 || otp.length() > 6) {
            log.warn("Invalid OTP length: {}. OTP must be 4-6 digits.", otp);
            return false;
        }
        
        String normalizedPhone = PhoneUtil.normalizePhone(phone);
        if (normalizedPhone == null || normalizedPhone.isEmpty() || !PhoneUtil.isValidPhone(normalizedPhone)) {
            log.warn("Invalid phone number format: {} (normalized: {}). Skipping SMS OTP send.", phone, normalizedPhone);
            return false;
        }
        
        int attempt = 0;
        Exception lastException = null;
        int maxRetries = 3;
        
        while (attempt < maxRetries) {
            attempt++;
            try {
                String url = String.format("%s/%s/SMS/%s/%s/%s",
                    TWO_FACTOR_BASE_URL,
                    apiKey,
                    normalizedPhone,
                    otp,
                    otpTemplateName);
                
                log.info("2Factor OTP SMS URL for phone {}: {}", phone, url);
                
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                
                log.info("2Factor OTP SMS response for phone {} (attempt {}/{}): {}", phone, attempt, maxRetries, response.getBody());
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    String responseBody = response.getBody();
                    if (responseBody.contains("Status") && (responseBody.contains("Success") || responseBody.contains("success"))) {
                        return true;
                    }
                }
                
                log.warn("2Factor API returned non-success response: {}", response.getBody());
            } catch (Exception e) {
                lastException = e;
                log.warn("Error sending OTP via 2Factor for phone {} (attempt {}/{}): {}", phone, attempt, maxRetries, e.getMessage());
                
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1000 * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Interrupted during retry delay", ie);
                        return false;
                    }
                }
            }
        }
        
        log.error("Failed to send OTP via 2Factor after {} attempts for phone: {}", maxRetries, phone, lastException);
        return false;
    }
    
    @Override
    public void sendSms(String phone, String message) {
        sendSmsWithRetry(phone, message, 1);
    }
    
    @Override
    public boolean sendSmsWithRetry(String phone, String message, int maxRetries) {
        if (phone == null || phone.isEmpty()) {
            log.info("Phone number not provided. Skipping SMS send. Email is primary channel.");
            return false;
        }
        
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("2Factor API key not configured. Skipping SMS send. Message: {}", message);
            return false;
        }
        
        String normalizedPhone = phone.replace("+", "").replaceAll("[^0-9]", "");
        if (normalizedPhone.length() < 10) {
            log.warn("Invalid phone number format: {}. Skipping SMS send.", phone);
            return false;
        }
        
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < maxRetries) {
            attempt++;
            try {
                String encodedMessage = java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8);
                String url = String.format("%s/%s/SMS/%s/%s", 
                    TWO_FACTOR_BASE_URL, 
                    apiKey, 
                    normalizedPhone, 
                    encodedMessage);
                
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                
                log.info("2Factor SMS response for phone {} (attempt {}/{}): {}", phone, attempt, maxRetries, response.getBody());
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    String responseBody = response.getBody();
                    if (responseBody.contains("Status") && (responseBody.contains("Success") || responseBody.contains("success"))) {
                        return true;
                    }
                }
                
                log.warn("2Factor API returned non-success response: {}", response.getBody());
            } catch (Exception e) {
                lastException = e;
                log.warn("Error sending SMS via 2Factor for phone {} (attempt {}/{}): {}", phone, attempt, maxRetries, e.getMessage());
                
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1000 * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Interrupted during retry delay", ie);
                        return false;
                    }
                }
            }
        }
        
        log.error("Failed to send SMS via 2Factor after {} attempts for phone: {}", maxRetries, phone, lastException);
        return false;
    }
}

