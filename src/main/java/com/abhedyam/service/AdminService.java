package com.abhedyam.service;

import com.abhedyam.dto.AdminFeedbackResponse;
import com.abhedyam.dto.PageResponse;
import com.abhedyam.model.Feedback;
import com.abhedyam.model.User;
import com.abhedyam.model.enums.FeedbackCategory;
import com.abhedyam.repository.FeedbackRepository;
import com.abhedyam.repository.UserRepository;
import com.abhedyam.util.PageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
/** Feedback list for the admin dashboard; shops, access and numbers live in AdminDashboardService. */
public class AdminService {
    
    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    
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

