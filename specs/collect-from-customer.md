# Collect a payment from a customer (oldest dues first)

## Problem
Recording a payment today needs the customer AND one specific sale item, and
cannot exceed that item's balance. Village shopkeepers collect a lump sum per
visit; splitting it across items by hand is slow and error-prone, and blocks a
fast "Got Rs X from Ravi" flow in the app.

## Solution (additive)
`POST /api/v1/owners/{ownerId}/payments/collect`
Body: `{ customerId, amount, medium? (default CASH), reference? }`
- Allocates `amount` across the customer's sale items that still have a
  balance, oldest first (`createdAt` ascending), splitting across items as
  needed. Each piece is created through the existing manual-payment path, so
  balance updates, status, audit and cache invalidation are unchanged.
- All-or-nothing: one transaction. If `amount` exceeds the customer's total
  outstanding balance the whole request is rejected with `PAYMENT_EXCEEDS_DUE`
  (same rule as today; advances/overpayment are not supported).
- Response: `{ customerId, totalCollected, payments[] }` (one PaymentResponse
  per allocated item).

## Paid on an earlier day (`paidAt`)
Optional `paidAt` (ISO instant) in the body: the owner records a payment taken
earlier (paper notebook, a missed day). Every row of that collect gets it as
its `timestamp`, so Collected, statements, "last paid" and month totals count
it on that day. The Recent activity row still shows when it was entered.
Not in the future (5 minutes of clock slack) and at most 366 days back, else
`VALIDATION_ERROR`. Omitted = now. A write-off ignores it.

## Validation / access
- Path owner must be the current user (existing owner check).
- Customer must belong to that owner, else `UNAUTHORIZED`.
- `amount > 0`, else `INVALID_AMOUNT`. No outstanding items -> `NO_DUE_AMOUNT`.

## Edge cases
- Items with null `remainingAmount` use price x quantity (existing rule).
- Rounding: amounts are 2-decimal BigDecimal; last piece takes the remainder.
- Double submit: not idempotent (client must disable the button); an
  idempotency key is future work.

## Out of scope
Advances/overpayments, choosing a specific item (existing endpoint), due-date
ordering, credit entries without a product.

## Changes
`dto/CollectPaymentRequest`, `dto/CollectPaymentResponse`,
`service/PaymentAllocator` (pure, unit-tested), `PaymentService`/`IPaymentService`,
`SaleItemRepository`, `OwnerPaymentController`.
