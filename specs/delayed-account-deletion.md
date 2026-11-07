# Delayed account deletion (disable, not delete)

## Behaviour
- `DELETE /api/v1/owner/account {confirm:"DELETE"}` no longer deletes. It records `owner_settings.deletion_requested_at`
  and returns `{deleted:false, scheduled:true, scheduledFor}` (48 h later). Asking twice does not move the date.
- `GET /owner/account/deletion` → `{scheduled, scheduledFor?}`; `POST /owner/account/deletion/cancel` clears the request.
- `AccountDeletionJob` (every 15 min):
  - 12 h after the request: emails the owner's data export (customers/sales/payments CSVs) once, ignoring the weekly
    limit, with a note saying when the account ends and how to keep it. No email on file or Gmail failure → retried each tick.
  - 48 h after: `disableAccount`.
- `disableAccount` keeps the ledger (sales, payments, items, customers, notes, documents, UPI, subscription) and:
  - owner: `is_active=false`, `deleted_at`, email → `former_email`, firebase uid / phone_normalized / public_slug cleared,
    public listing off; the Firebase user is deleted (a new sign-in makes a fresh one);
  - customers: inactive, `phone_normalized` cleared (plain `phone` stays);
  - removed: reminders, notifications, FCM tokens, pay links, owner settings (stops all outbound messages).
- JWT filter rejects an inactive owner (old tokens stop working).
- Same Gmail signs up again as a brand-new owner (login finds no row by uid/email); same customer phones can be added again.

## Decisions / known limits
- Disabled data is kept 90 days (`DISABLED_RETENTION`, owner's decision), then the job calls `deleteAccount` (hard delete of
  the owner and everything scoped to them; subscription/billing rows stay). Play's user-data policy and India's DPDP Act
  expect retention to be declared: the privacy page, delete-account page and Data safety form must say what is kept for 90 days and why.
- Customers of a disabled shop lose customer-app access (their phone number is freed).
- Old app (1.11.1) calls the same endpoint: it shows 'deleted' and logs out, the deletion proceeds after 48 h; it cannot cancel.
