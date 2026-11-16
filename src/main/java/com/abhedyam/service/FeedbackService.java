package com.abhedyam.service;

import com.abhedyam.dto.FeedbackCreateRequest;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Feedback;
import com.abhedyam.repository.FeedbackRepository;
import com.abhedyam.repository.UserRepository;
import com.abhedyam.service.interfaces.IFeedbackService;
import com.abhedyam.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedbackService implements IFeedbackService {
    
    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    
    @Override
    @Transactional
    public Feedback create(FeedbackCreateRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }
        
        String description = request.getIssueDescription().trim();
        if (description.isEmpty()) {
            throw new BusinessException("INVALID_DESCRIPTION", "Issue description cannot be empty");
        }
        
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setCategory(request.getCategory());
        feedback.setIssueDescription(description);
        
        String imageUrl = request.getImageUrl();
        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            feedback.setImageUrl(imageUrl.trim());
        }
        
        return feedbackRepository.save(feedback);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Feedback> mine() {
        return feedbackRepository.findByUserIdOrderByCreatedAtDesc(SecurityUtil.getCurrentUserId());
    }
}

