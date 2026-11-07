package com.abhedyam.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FirebaseService {
    
    public FirebaseToken verifyIdToken(String idToken) {
        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            log.info("Firebase token verified for UID: {}", decodedToken.getUid());
            return decodedToken;
        } catch (FirebaseAuthException e) {
            log.error("Firebase token verification failed: {}", e.getMessage());
            throw new com.abhedyam.exception.BusinessException("INVALID_FIREBASE_TOKEN", "Invalid or expired Firebase token");
        }
    }

    public String getEmailFromToken(FirebaseToken token) {
        return token.getEmail();
    }
    
    public String getNameFromToken(FirebaseToken token) {
        Object nameClaim = token.getClaims().get("name");
        if (nameClaim != null) {
            return nameClaim.toString();
        }
        return null;
    }
    
    public String getPictureFromToken(FirebaseToken token) {
        Object pictureClaim = token.getClaims().get("picture");
        if (pictureClaim != null) {
            return pictureClaim.toString();
        }
        return null;
    }
    
    public String getUidFromToken(FirebaseToken token) {
        return token.getUid();
    }
    
    public Boolean isEmailVerifiedFromToken(FirebaseToken token) {
        Object emailVerified = token.getClaims().get("email_verified");
        if (emailVerified instanceof Boolean) {
            return (Boolean) emailVerified;
        }
        return false;
    }
}

