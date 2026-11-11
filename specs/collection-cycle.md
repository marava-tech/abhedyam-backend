# Collection cycle per customer

### Problem
Many village customers pay a fixed amount on a fixed day: "₹500 every Monday", "₹2,000 on the 5th of every month". Today the owner has to add a new due date by hand each time.

### Solution
Each customer can have one collection cycle: weekly (a weekday) or monthly (a day of the month) plus an amount. The server works out the next collection from it. Nothing is stored per occurrence.

The cycle shows up as a due in the owner-wide due lists (Upcoming collections, Home card, digest "dues today" line, day-before reminder push), marked with `cycle`.

How a cycle's date is worked out (owner's time zone):
- `last` = latest cycle day on or before today, `prev` = the one before it. Monthly days past the month's end fall on its last day (31 → 30 Nov).
- `collected` = successful payments with `timestamp` after `prev`, up to now.
- `expected` = min(amount, customer balance). Balance 0, or chasing paused → no row.
- `last` before the cycle was set (`since`), or `collected ≥ expected` → next cycle day after today, status `OPEN`, paid 0.
- Otherwise `last` is shown: paid = collected, remaining = expected − collected, status `OPEN` if `last` is today, else `OVERDUE`.

### API Contract
`PUT /api/v1/owners/{ownerId}/customers/{customerId}/cycle` body `{ "kind": "WEEKLY"|"MONTHLY", "day": int, "amount": number }` → `CustomerResponse`
- WEEKLY: day 1-7 (Mon-Sun). MONTHLY: day 1-31. amount > 0.
- Saving sets `since` = today; changing it starts the count again.
- 400 VALIDATION_ERROR for a bad kind/day/amount. 404 if the customer isn't the owner's.

`DELETE` same path → `CustomerResponse` (cycle removed).

`CustomerResponse.cycle`: `{ kind, day, amount }` or null.

Owner-wide lists (`GET /owners/{ownerId}/dues`, digest, reminders): one extra `OwnerDueResponse` per customer with a cycle. Its `due` has
`id` = fixed per customer (UUID from "cycle:" + customerId), `amount` = expected, `paid`, `remaining`, `dueDate`, `status`, `cycle = { kind, day, amount }`, `note` null. Real dues have `cycle` null.
Edit/delete on that id = 404 (it isn't a due); "Got paid" works as a normal payment.

### Data Model
`customers`: `cycle_kind VARCHAR(10)`, `cycle_day INT`, `cycle_amount DECIMAL(15,2)`, `cycle_since DATE`, all nullable.

### Edge Cases
- Customer also has normal dues: both show; a payment counts for both (dues use the allocator, the cycle looks at payments since `prev`).
- Paid more than one cycle's amount early: the next cycle isn't credited; only the window since `prev` counts.
- Missed several cycles: only the latest one shows as overdue, amount capped at one cycle (ponytail: no backlog sum).
- Balance below the amount: expected = the balance.

### Out of Scope
Every-N-weeks/days cycles, several cycles per customer, a backlog of missed cycles, sending anything to the customer.
