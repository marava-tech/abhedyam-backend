package com.abhedyam.util;

import java.util.regex.Pattern;

public class PhoneUtil {
    
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[0-9]{10,15}$");
    
    public static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String normalized = phone.replaceAll("[^0-9+]", "");
        if (!normalized.startsWith("+")) {
            if (normalized.startsWith("91") && normalized.length() == 12) {
                normalized = "+" + normalized;
            } else if (normalized.length() == 10) {
                normalized = "+91" + normalized;
            }
        }
        return normalized;
    }
    
    public static boolean isValidPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }
        String normalized = normalizePhone(phone);
        return PHONE_PATTERN.matcher(normalized).matches();
    }
    
    public static String extractPhoneWithoutCountryCode(String normalizedPhone) {
        if (normalizedPhone == null || normalizedPhone.isEmpty()) {
            return null;
        }
        String phone = normalizedPhone.replace("+", "");
        if (phone.startsWith("91") && phone.length() == 12) {
            return phone.substring(2);
        }
        return phone;
    }
    
}

