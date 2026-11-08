# Old dues: stop chasing + write off

Owner decision (2026-11-08): for dues that will not come back, offer both.

## Stop chasing (no money change)
- `customers.chase_paused` (BOOLEAN, null = false). `CustomerResponse.chasePaused`.
- `PUT /api/v1/owners/{ownerId}/customers/{customerId}/chase` body `{ "paused": true|false }` → 200 `CustomerResponse`.
- The app leaves paused customers out of Who to chase / Old dues; balance stays in To collect.

## Write off (money change, kept in the ledger)
- `payments.write_off` (BOOLEAN, null = false). Not a new `PaymentMedium` value: app 1.12 and the customer app
  throw on unknown media; a write-off is stored as medium `OFFLINE` + `writeOff=true`, so old apps show it as an
  "Offline" entry instead of failing.
- `POST /api/v1/owners/{ownerId}/payments/write-off` body `{ "customerId": "…" }` → writes off the customer's whole
  open balance through the same allocation as collect-from-customer (oldest sale first), one payment row per sale,
  `reference = "Written off"`. 400 `NO_DUE_AMOUNT` when nothing is owed. Returns `CollectPaymentResponse`.
- Undo: `DELETE /api/v1/owners/{ownerId}/payments/write-off/{customerId}` puts each amount back on its sale and deletes the written-off rows.
- `PaymentResponse.writeOff`.
- **Balances include write-offs** (`sumSuccessfulByCustomer`, sale `remainingAmount`) → To collect drops.
- **Money in excludes write-offs**: collected today / week / month (`sumSuccessfulBetween`, `countSuccessfulBetween`),
  total collected in the owner summary, `lastSuccessfulPaymentByCustomer` ("Paid 25 Jun"),
  `countSuccessfulPaymentsByOwnerIdAndDateRange`. The app also hides write-offs from the Collected list.
- No push, SMS or notification for a write-off.

## Tests
- Repository: write-off rows count in `sumSuccessfulByCustomer`, not in `sumSuccessfulBetween` /
  `countSuccessfulBetween` / `lastSuccessfulPaymentByCustomer`.
