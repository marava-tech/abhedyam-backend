package com.abhedyam.service.interfaces;

import com.abhedyam.dto.CustomerBatchRequest;
import com.abhedyam.dto.CustomerBatchResult;

import java.util.UUID;

public interface ICustomerBatchService {
    CustomerBatchResult save(UUID ownerId, CustomerBatchRequest request, boolean dryRun);
}
