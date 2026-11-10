package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.CustomerCreateRequest;
import com.abhedyam.dto.NearestCustomerRequest;
import com.abhedyam.dto.NearestCustomerResponse;
import com.abhedyam.model.Customer;
import com.abhedyam.service.interfaces.ICustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Customer management APIs")
public class CustomerController {
    
    private final ICustomerService customerService;
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Customer> create(@Valid @RequestBody CustomerCreateRequest request) {
        return ApiResponse.success(customerService.create(request));
    }
    
    @GetMapping("/{id}")
    public ApiResponse<Customer> getById(@PathVariable UUID id) {
        return ApiResponse.success(customerService.getById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update customer details", description = "Update customer name, phone, or village")
    public ApiResponse<Customer> update(@PathVariable UUID id, @RequestBody com.abhedyam.dto.CustomerUpdateRequest request) {
        return ApiResponse.success(customerService.update(id, request));
    }
    
    @PostMapping("/nearest")
    public ApiResponse<NearestCustomerResponse> findNearestCustomer(@Valid @RequestBody NearestCustomerRequest request) {
        return ApiResponse.success(customerService.findNearestCustomer(request));
    }
}

