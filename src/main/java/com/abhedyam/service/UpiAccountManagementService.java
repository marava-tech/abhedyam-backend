package com.abhedyam.service;

import com.abhedyam.dto.UpiAccountCreateRequest;
import com.abhedyam.dto.UpiAccountResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Customer;
import com.abhedyam.model.UPIAccount;
import com.abhedyam.model.User;
import com.abhedyam.model.enums.UserType;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.UPIAccountRepository;
import com.abhedyam.repository.UserRepository;
import com.abhedyam.service.interfaces.IUpiAccountManagementService;
import com.abhedyam.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpiAccountManagementService implements IUpiAccountManagementService {
    
    private final UPIAccountRepository upiAccountRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public UpiAccountResponse getUpiAccountByOwnerId(UUID ownerId) {
        UUID currentUserId = SecurityUtil.getCurrentUserId();
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
        
        boolean hasAccess = false;
        
        if (currentUser.getType() == UserType.BUSINESS && currentUserId.equals(ownerId)) {
            hasAccess = true;
        } else if (currentUser.getType() == UserType.CUSTOMER) {
            Customer customer = customerRepository.findById(currentUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
            if (customer.getOwnerId() != null && customer.getOwnerId().equals(ownerId)) {
                hasAccess = true;
            }
        }
        
        if (!hasAccess) {
            throw new BusinessException("UNAUTHORIZED", "You don't have permission to access this UPI account");
        }
        
        UPIAccount account = upiAccountRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("UPI Account not found for owner"));
        return toResponse(account);
    }
    
    @Override
    @Transactional
    public UpiAccountResponse updateCurrentUserUpiAccount(UpiAccountCreateRequest request) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        
        if (upiAccountRepository.findByVpa(request.getVpa())
                .filter(acc -> !acc.getId().equals(ownerId))
                .isPresent()) {
            throw new BusinessException("VPA_ALREADY_EXISTS", "VPA already exists");
        }
        
        UPIAccount upiAccount = upiAccountRepository.findByOwnerId(ownerId)
                .orElseGet(() -> {
                    UPIAccount newAccount = new UPIAccount();
                    newAccount.setId(ownerId);
                    newAccount.setOwnerId(ownerId);
                    return newAccount;
                });
        
        upiAccount.setVpa(request.getVpa());
        upiAccount.setOwnerId(ownerId);
        
        UPIAccount saved = upiAccountRepository.save(upiAccount);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public UpiAccountResponse updateUpiAccountForOwner(UUID ownerId, UpiAccountCreateRequest request) {
        validateOwnerAccess(ownerId);
        return updateCurrentUserUpiAccount(request);
    }

    @Override
    @Transactional
    public UpiAccountResponse verifyCurrentUserVpa() {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        
        UPIAccount upiAccount = upiAccountRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("UPI Account not found for current user"));
        
        upiAccount.setIsVerified(true);
        upiAccount.setVerifiedAt(Instant.now());
        
        UPIAccount saved = upiAccountRepository.save(upiAccount);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public UpiAccountResponse verifyVpaForOwner(UUID ownerId) {
        validateOwnerAccess(ownerId);
        return verifyCurrentUserVpa();
    }
    
    private UpiAccountResponse toResponse(UPIAccount account) {
        UpiAccountResponse response = new UpiAccountResponse();
        response.setId(account.getId());
        response.setVpa(account.getVpa());
        response.setIsVerified(account.getIsVerified());
        response.setVerifiedAt(account.getVerifiedAt());
        response.setCreatedAt(account.getCreatedAt());
        response.setUpdatedAt(account.getUpdatedAt());
        return response;
    }

    private void validateOwnerAccess(UUID ownerId) {
        UUID currentOwnerId = SecurityUtil.getCurrentUserId();
        if (ownerId == null || !ownerId.equals(currentOwnerId)) {
            throw new BusinessException("UNAUTHORIZED", "You can only access your own UPI account");
        }
    }
}

