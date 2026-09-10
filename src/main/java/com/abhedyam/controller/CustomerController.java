package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.CustomerCreateRequest;
import com.abhedyam.dto.CustomerSummaryResponse;
import com.abhedyam.dto.NearestCustomerRequest;
import com.abhedyam.dto.NearestCustomerResponse;
import com.abhedyam.model.Customer;
import com.abhedyam.service.interfaces.ICustomerService;
import com.abhedyam.service.interfaces.ISubscriptionService;
import com.abhedyam.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Customer management APIs")
public class CustomerController {
    
    private final ICustomerService customerService;
    private final ISubscriptionService subscriptionService;

    @Value("${app.subscription.enforce:false}")
    private boolean enforcePro;
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Customer> create(@Valid @RequestBody CustomerCreateRequest request) {
        return ApiResponse.success(customerService.create(request));
    }
    
    @GetMapping("/{id}")
    public ApiResponse<Customer> getById(@PathVariable UUID id) {
        return ApiResponse.success(customerService.getById(id));
    }
    
    @GetMapping("/me/summary")
    @Operation(summary = "Get customer summary", description = "Get customer's own summary including sales, payments, and dues. Only accessible by the customer themselves.")
    public ApiResponse<CustomerSummaryResponse> getMySummary() {
        return ApiResponse.success(customerService.getMySummary());
    }
    
    @PostMapping("/nearest")
    public ApiResponse<NearestCustomerResponse> findNearestCustomer(@Valid @RequestBody NearestCustomerRequest request) {
        if (enforcePro) {
            subscriptionService.ensureProSubscription(SecurityUtil.getCurrentUserId());
        }
        return ApiResponse.success(customerService.findNearestCustomer(request));
    }
}

