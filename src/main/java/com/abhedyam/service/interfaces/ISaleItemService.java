package com.abhedyam.service.interfaces;

import com.abhedyam.dto.SaleItemResponse;

import java.util.List;
import java.util.UUID;

public interface ISaleItemService {
    List<SaleItemResponse> getByCustomerIdForOwner(UUID ownerId, UUID customerId, boolean expandProduct);
}
