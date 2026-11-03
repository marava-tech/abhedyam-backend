# Per-village pending and this-month collected

## Problem
The owner app's new Home ("this month's round") needs to show, per village,
how much is still to be collected, and how much was collected this month.
Today `GET /owners/{id}/summary` only returns all-time collected, and
`GET /location-details/villages` only returns customer counts.

## Solution (additive, no breaking change)
- `GET /owners/{ownerId}/summary` gains `collectedThisMonth`: sum of SUCCESS
  payments whose `timestamp` falls in the current calendar month, Asia/Kolkata.
- `GET /location-details/villages` (paginated endpoint only) gains
  `pendingAmount` per village: sum over active customers in that village of
  `max(0, sales - successful payments)`, i.e. the same per-customer definition
  already used by the customer list, with overpaid (advance) customers
  contributing 0 so they never offset others' dues.
- The non-paginated/search village lists keep `pendingAmount = null` (unchanged).

## Notes
- Aggregation uses grouped queries (one row per customer), not row loading.
- `totalPendingAmount` on the summary is unchanged (still global, clamped at 0).
  The sum of village `pendingAmount` can therefore exceed it when some
  customers are overpaid; this is intended and documented.
- Customers with no village are not part of any village row.

## Changes
- `dto/OwnerSummaryResponse`, `dto/VillageResponse`: new fields.
- `repository/{SaleItem,Payment,LocationDetails}Repository`: grouped queries.
- `service/OwnerService`: month window + new field.
- `service/LocationDetailsService`: attaches pending to paginated villages.
- `service/VillagePendingCalculator`: pure aggregation helper (unit-tested).

## Compatibility
Fields are additive; old clients ignore them.
