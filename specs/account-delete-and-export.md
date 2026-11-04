# Delete my account + export my data (owner)

## Problem
Google Play requires apps that let users create an account to offer account and
data deletion (in-app path + a public web link). The app has neither. Owners also
have no way to take their khata with them (lost-phone / switching fear, trust).

## Solution
Two authenticated owner endpoints.
1. **Delete account**: permanently removes the owner and everything scoped to them,
   then the app signs out. Confirmed by sending the literal word `DELETE`.
2. **Export data**: emails the owner a zip of CSVs (customers, sales, payments), at most once a week.

## API Contract
All under `/api/v1/owner/account`, owner JWT required (current user = owner).
- `DELETE /api/v1/owner/account` body `{ "confirm": "DELETE" }` → `200 { success, data: { deleted: true } }`
  - 400 `CONFIRMATION_REQUIRED` if `confirm` is not exactly `DELETE`
  - 403 if the caller is not an owner
- `POST /api/v1/owner/account/export-request` (no body) → `202 { success, data: { status: "QUEUED", email: "p***x@gmail.com" } }`
  - Builds a zip (`customers.csv`, `sales.csv`, `payments.csv`) in the background and **emails it to the owner's
    profile email**; it can take a few minutes. The app only shows "We'll email it to p***x@gmail.com".
  - **Rate limit: one request per owner per 7 days.** A second request inside the window → `429 RATE_LIMITED`
    ("You can export once a week. Try again on 11 Nov 2026"). If building/sending fails, the weekly allowance is given back.
  - 400 `EMAIL_REQUIRED` when the owner has no email on the profile.
  - CSV columns
    - customers: `Name, Phone, Village, Pending amount, Added on`
    - sales: `Date, Customer, Item, Quantity, Price, Total, Still due, Status, Due date, Bill id`
    - payments: `Date, Customer, Amount, Paid by, Status, Reference`
  - newest first; amounts as plain numbers; dates `yyyy-MM-dd HH:mm` IST; UTF-8 with BOM; cells starting
    with `= + - @` are prefixed with `'` (CSV-injection guard); commas/quotes/newlines are quoted.

## Data Model
`owner_settings.last_export_requested_at` (nullable timestamp) records the last export request.
Delete removes, for the owner id: sale_items, payments, products,
inventories, reminders, notes, documents, upi_accounts, audits, notifications,
owner_settings, daily_stats, top_products, payment_links, customers (and each
customer's location_details, fcm_details, notifications), then the owner's own
fcm_details, feedbacks, location_details, and finally the owner/user row.
Kept: `subscriptions` rows (billing records; hold only ids and amounts, no name or phone).
Best effort after the DB commit: delete the Firebase user.

## Edge Cases
- Whole delete is one transaction: any failure rolls back and the account stays intact.
- Calling delete twice: the second call is rejected as unauthenticated/not found (no error leak).
- Firebase user already gone / Firebase down: log and continue; DB deletion already committed.
- Large accounts (thousands of rows): bulk JPQL deletes for plain tables; customers removed in one batch.
- Uploaded files (documents/images on the file store) are not deleted by this call (Out of scope).
- Export of an empty list gives CSVs with just the header row.
- Two requests in the same instant could both pass the weekly check (accepted at this scale).
- Email delivery failure is logged and refunds the weekly allowance; the owner is not otherwise notified.

## Out of Scope
Soft-delete / grace period or undo; deleting uploaded files from storage and Redis
cache entries (cache expires by TTL); the public web deletion-request page (website repo);
Excel/PDF formats; export of reminders, notes and documents;
admin-initiated deletion; deleting data of the owner's customers who also use the Customer app.
