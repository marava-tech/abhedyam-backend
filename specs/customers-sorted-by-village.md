# Customers listed in village order

## Problem
`GET /api/v1/owners/{ownerId}/customers` defaulted to newest-first
(`createdAt DESC`). Shopkeepers collect and reconcile dues village by village,
so a flat chronological list forces them to hunt. The village lives in
`location_details`, not on `customers`, so a plain `sortBy` could not reach it.

## Solution
- Default sort is now **village**: customers grouped by village
  (case- and whitespace-insensitive), then by name; customers with no village
  row sort last.
- Two new repository queries (`searchCustomersOrderByVillage`,
  `searchCustomersWithVillageFilterOrderByVillage`) carry the `ORDER BY` on the
  joined `location_details.village`. No `DISTINCT` — `user_id` is unique so the
  LEFT JOIN is 1:0..1, and MySQL rejects `ORDER BY` on a joined column under
  `DISTINCT`. Explicit `countQuery` on both.
- `sortBy=createdAt` (or any other Customer field) with `sortDirection` still
  works exactly as before.
- Controller default `sortBy` changed `createdAt` -> `village`; OpenAPI text
  updated.

## Changes
- `repository/CustomerRepository.java` — 2 new paged queries
- `service/CustomerService.java` — `getOwnerCustomers` branches on village sort
- `controller/OwnerCustomerController.java` — default param + docs

## Compatibility
Response shape unchanged. Clients that don't pass `sortBy` (duesetu web,
Flutter apps) get the new village order automatically — the desired behaviour.
