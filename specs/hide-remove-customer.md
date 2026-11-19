# Hide or remove a customer

Owner asked (2026-11-19): a way to stop seeing a customer who stopped buying, so they no longer come up in lists, village groups and searches. Research (Zoho, Sage, QuickBooks, Wave): delete only when there is no history; otherwise mark inactive, keep the records, clear the balance first.

## Rules
- **Hide** (`POST /api/v1/owners/{ownerId}/customers/{customerId}/hide`): only at ₹0 balance (sales − successful payments). Otherwise 400 `CUSTOMER_OWES` with the amount in the message. The app offers "Forget ₹X and hide": the existing write-off (`.../payments/write-off`), then hide.
  Stored as `users.is_active = false` (already skipped by village counts, dues and the chase list; until now only set when an account is deleted, together with `deleted_at`).
- **Show again** (`POST .../{customerId}/show`): `is_active = true`.
- **Hidden list** (`GET .../customers/hidden`): hidden customers of this owner, by name.
- **Comes back by itself**: a new sale for them (SaleService already reactivates), or adding a customer with the same phone, or the same name + village.
- **Remove** (`DELETE .../customers/{customerId}`): only with no sales and no payments, else 400 `CUSTOMER_HAS_RECORDS`. Deletes their due dates, notes, reminders, audits, location, and the customer. The app's Undo is a 5 s delay before it calls this.
- Left out while hidden: customer list and search (all orders, all filters), New sale picker (same search), nearby/route locations. Kept: reports and totals (history doesn't change), customer page by id, statement, khata link.
- "N customers" (owner summary) counts only shown customers.
- `CustomerResponse.hidden` tells the app to show "Hidden · Show again" on the customer page.
