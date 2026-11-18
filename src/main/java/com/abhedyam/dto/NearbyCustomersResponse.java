package com.abhedyam.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NearbyCustomersResponse {
    /** Customers who owe and have a saved location, closest first. */
    private List<NearbyCustomer> customers;
    /** Customers who owe but have no saved location yet. */
    private long owingWithoutLocation;
}
