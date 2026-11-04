# Sale: quantity, paid-now, walk-in, client idempotency key

## Problem
The New sale flow in the owner app cannot send a quantity (5 units = add the item 5
times), cannot say a sale was paid at the counter (cash sale = sale + a second
"payment in" flow), requires a named customer (no counter/walk-in sale), and the
server de-duplicates sales by customer+products for 5 minutes, which silently
swallows a genuine repeat sale of the same item. See owner app
`.product/NEW_SALE_REDESIGN.md`.

## Solution (additive, all new fields optional; old clients unchanged)
`POST /api/v1/sales` accepts, in addition to today's body:
- item `quantity` (decimal, 0.001..99999.999, default 1)
- `paidAmount` (>= 0) + `paymentMedium` (default CASH): payment taken at sale time
- `walkIn` (boolean): sell to the owner's "Walk-in" customer (created on first use)
- `idempotencyKey` (string, <= 64): client-generated per sale attempt

## API Contract
- Line total = unit price x quantity. It must be a whole number of paise
  (<= 2 decimals); otherwise `INVALID_QUANTITY`.
- Sale total = sum of line totals. `paidAmount` > total -> `PAYMENT_EXCEEDS_DUE`.
  `paidAmount` is allocated across THIS sale's items in order (existing
  `PaymentAllocator`), each piece recorded through the existing manual-payment
  path (status SUCCESS) so balances, status, audit and caches are unchanged.
  Remaining balance stays on the items (NOT_PAID / PARTIALLY_PAID / PAID).
- `walkIn=true` (and no customerId): customer = owner's "Walk-in" (find by name,
  else create, no phone, village "No Village"). A walk-in sale must be paid in
  full, else `WALK_IN_MUST_BE_PAID`.
- `idempotencyKey` present: a second request with the same key (same owner,
  within 5 min) returns the first sale and does nothing else, checked BEFORE any
  customer/product is created. Absent: legacy customer+products behaviour.
- Stock out uses the real quantity.
- Response unchanged: `SaleDetailResponse` already has `totalPaid` / `totalDue`.
- Errors: `INVALID_QUANTITY`, `PAYMENT_EXCEEDS_DUE`, `INVALID_AMOUNT`,
  `WALK_IN_MUST_BE_PAID` are returned as-is (not folded into SALE_CREATION_FAILED).
  All-or-nothing in one transaction.

## Data Model
None. `SaleItem.quantity` already exists (decimal 18,3).

## Edge Cases
- 2.333 x 10.50 is not whole paise -> rejected; the app rounds/asks for a total.
- Same key twice -> one sale, one payment. Redis down -> falls back to creating
  (existing behaviour of the idempotency check).
- Walk-in customer appears in the customer list (v1). Hiding it is future work.
- Payment record: no customer SMS/notification is sent on this path.

## Out of Scope
Discounts (needs one `lineTotal` refactor across ~25 total computations and the
`SUM(price*quantity)` JPQL), sale notes, units (kg/pcs), hiding walk-in, editing a
sale after creation.
