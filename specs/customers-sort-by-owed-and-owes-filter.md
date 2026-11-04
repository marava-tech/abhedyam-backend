# Customers: sort by most owed + "owes you" filter

## Problem
The owner's customer list can only be ordered by village or a stored column. Pending
amounts are computed after paging, so an app cannot show "who owes me the most" or
"only customers who owe me" beyond the first page. Chasing the biggest dues first is the core job.

## Solution
Two optional query params on the existing owner customer list; nothing changes when they are absent.
- `sortBy=pendingAmount` (with `sortDirection=DESC` default for this key = most owed first, `ASC` = least owed first)
- `owesOnly=true` — only customers whose pending amount is greater than zero

Both work together with the existing `q` (search) and `village` filters and with the default village ordering
(`owesOnly=true` alone keeps village order). Pending amount = sale totals − successful payments (same as `includePendingAmountDetails`).
When either option is used, every returned customer has `pendingAmount` set.

## API Contract
`GET /api/v1/owners/{ownerId}/customers?...&sortBy=pendingAmount&sortDirection=DESC&owesOnly=true`
- Response shape unchanged (`PageResponse<CustomerResponse>`); `totalElements` reflects the filtered count.
- Ties (equal pending) are ordered by name, then id.
- Errors: unchanged (400 for unknown sort column on the stored-column path).

## Data Model
None.

## Edge Cases
- Customers with no sales: pending 0, hidden by `owesOnly`, last in `sortBy=pendingAmount DESC`.
- Negative pending (overpaid): treated as not owing (`owesOnly` excludes it); sorts below zero-balance ones in DESC.
- Pending/failed payments do not reduce the amount owed.
- Page beyond the end returns an empty content list.
- Cost: computed in memory for the owner's customers (thousands at most); revisit with an aggregate SQL view if an owner exceeds ~20k customers.

## Out of Scope
Sorting by last payment date or overdue age, filtering by amount range, client-side caching, changing the default order.
