package com.abhedyam.service.interfaces;

import com.abhedyam.dto.CustomerCreateRequest;
import com.abhedyam.dto.CustomerResponse;
import com.abhedyam.dto.CustomerSearchRequest;
import com.abhedyam.dto.CustomerBasicSummaryResponse;
import com.abhedyam.dto.CustomerSalesSummaryResponse;
import com.abhedyam.dto.CustomerPaymentsSummaryResponse;
import com.abhedyam.dto.CustomerNotesSummaryResponse;
import com.abhedyam.dto.CustomerRemindersSummaryResponse;
import com.abhedyam.dto.NearestCustomerRequest;
import com.abhedyam.dto.NearestCustomerResponse;
import com.abhedyam.dto.PageResponse;
import com.abhedyam.model.Customer;

import java.util.List;
import java.util.UUID;

public interface ICustomerService {
    Customer create(CustomerCreateRequest request);
    Customer update(UUID customerId, com.abhedyam.dto.CustomerUpdateRequest request);
    Customer getById(UUID id);
    List<Customer> getByOwnerId(UUID ownerId);
    List<CustomerResponse> filterCustomers(String searchText);
    PageResponse<Customer> searchCustomers(CustomerSearchRequest request);
    PageResponse<CustomerResponse> getOwnerCustomers(UUID ownerId, String searchText, String village, Integer page, Integer size, String sortBy, String sortDirection, boolean includePendingAmountDetails);

    /** Same list with optional "owes only" filter; sortBy=pendingAmount sorts by what is owed. */
    PageResponse<CustomerResponse> getOwnerCustomers(UUID ownerId, String searchText, String village, Integer page, Integer size, String sortBy, String sortDirection, boolean includePendingAmountDetails, boolean owesOnly);
    CustomerBasicSummaryResponse getCustomerBasicSummary(UUID ownerId, UUID customerId);
    CustomerSalesSummaryResponse getCustomerSalesSummary(UUID ownerId, UUID customerId);
    CustomerPaymentsSummaryResponse getCustomerPaymentsSummary(UUID ownerId, UUID customerId);
    CustomerNotesSummaryResponse getCustomerNotesSummary(UUID ownerId, UUID customerId);
    CustomerRemindersSummaryResponse getCustomerRemindersSummary(UUID ownerId, UUID customerId);
    NearestCustomerResponse findNearestCustomer(NearestCustomerRequest request);
    void invalidateCustomerSummaryCache(UUID ownerId, UUID customerId);
    com.abhedyam.dto.CustomerResponse setChasePaused(java.util.UUID ownerId, java.util.UUID customerId, boolean paused);

    /** null = remove the cycle. */
    com.abhedyam.dto.CustomerResponse setCycle(java.util.UUID ownerId, java.util.UUID customerId, com.abhedyam.dto.CollectionCycleDto cycle);
}
