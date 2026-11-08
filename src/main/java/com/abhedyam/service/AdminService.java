package com.abhedyam.service;

import com.abhedyam.dto.AdminFeedbackResponse;
import com.abhedyam.dto.AdminOwnerDetailResponse;
import com.abhedyam.dto.AdminOwnerListResponse;
import com.abhedyam.dto.PageResponse;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Feedback;
import com.abhedyam.model.Owner;
import com.abhedyam.model.User;
import com.abhedyam.model.enums.FeedbackCategory;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.FeedbackRepository;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.UserRepository;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.service.interfaces.ISubscriptionService;
import com.abhedyam.util.PageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {
    
    private final OwnerRepository ownerRepository;
    private final CustomerRepository customerRepository;
    private final SaleItemRepository saleItemRepository;
    private final PaymentRepository paymentRepository;
    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final ISubscriptionService subscriptionService;
    
    @Transactional(readOnly = true)
    public PageResponse<AdminOwnerListResponse> listOwners(String username, String email, 
                                                           Integer page, Integer size, 
                                                           String sortBy, String sortDirection) {
        Pageable pageable = PageUtil.createPageable(page, size, sortBy, sortDirection);
        
        Page<Owner> ownerPage = ownerRepository.searchOwners(username, email, pageable);
        List<Owner> owners = ownerPage.getContent();
        
        List<AdminOwnerListResponse> responses = owners.stream()
            .map(owner -> {
                AdminOwnerListResponse response = new AdminOwnerListResponse();
                response.setId(owner.getId());
                response.setName(owner.getName());
                response.setEmail(owner.getEmail());
                response.setCreatedAt(owner.getCreatedAt());
                response.setSubscription(owner.getSubscription());
                
                long customerCount = customerRepository.countByOwnerId(owner.getId());
                response.setTotalCustomers(customerCount);
                
                return response;
            })
            .collect(Collectors.toList());
        
        return new PageResponse<>(
            responses,
            ownerPage.getNumber(),
            ownerPage.getSize(),
            ownerPage.getTotalElements(),
            ownerPage.getTotalPages(),
            ownerPage.hasNext(),
            ownerPage.hasPrevious()
        );
    }
    
    @Transactional(readOnly = true)
    public AdminOwnerDetailResponse getOwnerDetails(UUID ownerId, Instant startDate, Instant endDate) {
        Owner owner = ownerRepository.findById(ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        
        AdminOwnerDetailResponse response = new AdminOwnerDetailResponse();
        response.setId(owner.getId());
        response.setName(owner.getName());
        response.setEmail(owner.getEmail());
        response.setCreatedAt(owner.getCreatedAt());
        response.setSubscription(owner.getSubscription());
        
        long salesCount = saleItemRepository.countDistinctSalesByOwnerIdAndDateRange(
            ownerId, startDate, endDate);
        response.setTotalSales(salesCount);
        
        long paymentsCount = paymentRepository.countSuccessfulPaymentsByOwnerIdAndDateRange(
            ownerId, startDate, endDate);
        response.setTotalSuccessfulPayments(paymentsCount);
        
        List<Feedback> feedbacks = feedbackRepository.findByUserIdOrderByCreatedAtDesc(ownerId);
        List<UUID> feedbackUserIds = feedbacks.stream()
            .map(Feedback::getUserId)
            .distinct()
            .collect(Collectors.toList());
        
        List<User> feedbackUsers = userRepository.findAllById(feedbackUserIds);
        java.util.Map<UUID, User> feedbackUserMap = feedbackUsers.stream()
            .collect(Collectors.toMap(User::getId, user -> user, (v1, v2) -> v1));
        
        List<AdminFeedbackResponse> feedbackResponses = feedbacks.stream()
            .map(feedback -> {
                AdminFeedbackResponse feedbackResponse = new AdminFeedbackResponse();
                feedbackResponse.setId(feedback.getId());
                feedbackResponse.setUserId(feedback.getUserId());
                feedbackResponse.setCategory(feedback.getCategory());
                feedbackResponse.setIssueDescription(feedback.getIssueDescription());
                feedbackResponse.setImageUrl(feedback.getImageUrl());
                feedbackResponse.setCreatedAt(feedback.getCreatedAt());
                
                User user = feedbackUserMap.get(feedback.getUserId());
                if (user != null) {
                    feedbackResponse.setUserName(user.getName());
                    feedbackResponse.setUserEmail(user.getEmail());
                }
                
                return feedbackResponse;
            })
            .collect(Collectors.toList());
        response.setFeedbacks(feedbackResponses);
        
        return response;
    }
    
    @Transactional
    public void upgradeSubscription(UUID ownerId) {
        Owner owner = ownerRepository.findById(ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        
        subscriptionService.activateProPlanForTesting(ownerId);
        log.info("Activated SaaS subscription for owner {}", ownerId);
    }
    
    @Transactional
    public void downgradeSubscription(UUID ownerId) {
        Owner owner = ownerRepository.findById(ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        
        subscriptionService.downgradeToGoForTesting(ownerId);
        log.info("Expired SaaS subscription for owner {}", ownerId);
    }
    
    @Transactional(readOnly = true)
    public PageResponse<AdminFeedbackResponse> listFeedbacks(UUID userId, FeedbackCategory category, 
                                                              String searchText, Integer page, Integer size, 
                                                              String sortBy, String sortDirection) {
        Pageable pageable = PageUtil.createPageable(page, size, sortBy, sortDirection);
        
        Page<Feedback> feedbackPage = feedbackRepository.searchFeedbacks(userId, category, searchText, pageable);
        List<Feedback> feedbacks = feedbackPage.getContent();
        
        List<UUID> userIds = feedbacks.stream()
            .map(Feedback::getUserId)
            .distinct()
            .collect(Collectors.toList());
        
        List<User> users = userRepository.findAllById(userIds);
        java.util.Map<UUID, User> userMap = users.stream()
            .collect(Collectors.toMap(User::getId, user -> user, (v1, v2) -> v1));
        
        List<AdminFeedbackResponse> responses = feedbacks.stream()
            .map(feedback -> {
                AdminFeedbackResponse response = new AdminFeedbackResponse();
                response.setId(feedback.getId());
                response.setUserId(feedback.getUserId());
                response.setCategory(feedback.getCategory());
                response.setIssueDescription(feedback.getIssueDescription());
                response.setImageUrl(feedback.getImageUrl());
                response.setCreatedAt(feedback.getCreatedAt());
                
                User user = userMap.get(feedback.getUserId());
                if (user != null) {
                    response.setUserName(user.getName());
                    response.setUserEmail(user.getEmail());
                }
                
                return response;
            })
            .collect(Collectors.toList());
        
        return new PageResponse<>(
            responses,
            feedbackPage.getNumber(),
            feedbackPage.getSize(),
            feedbackPage.getTotalElements(),
            feedbackPage.getTotalPages(),
            feedbackPage.hasNext(),
            feedbackPage.hasPrevious()
        );
    }
}

