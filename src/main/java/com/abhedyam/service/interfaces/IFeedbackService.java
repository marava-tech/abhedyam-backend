package com.abhedyam.service.interfaces;

import com.abhedyam.dto.FeedbackCreateRequest;
import com.abhedyam.model.Feedback;

import java.util.List;

public interface IFeedbackService {
    Feedback create(FeedbackCreateRequest request);
    List<Feedback> mine();
}

