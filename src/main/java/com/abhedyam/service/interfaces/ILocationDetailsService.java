package com.abhedyam.service.interfaces;

import com.abhedyam.dto.CustomerLocationRequest;
import com.abhedyam.dto.CustomerLocationResponse;
import com.abhedyam.dto.LocationDetailsResponse;
import com.abhedyam.dto.LocationDetailsUpdateRequest;
import com.abhedyam.dto.PageResponse;

import java.util.List;
import java.util.UUID;

public interface ILocationDetailsService {
    LocationDetailsResponse getCustomerLocation(UUID customerId);
    LocationDetailsResponse getLocationByUserId(UUID userId);
    PageResponse<com.abhedyam.dto.VillageResponse> getVillagesPaginated(String name, Integer page, Integer size);
    LocationDetailsResponse updateCustomerLocation(UUID customerId, LocationDetailsUpdateRequest request);
    LocationDetailsResponse updateLocationForUser(UUID userId, LocationDetailsUpdateRequest request);
    List<CustomerLocationResponse> getCustomerLocations(CustomerLocationRequest request);
}

