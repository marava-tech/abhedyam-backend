# Customer khata link (no login)

## Problem
The customer app can't sign anyone in (phone-only login is off since #7) and customers won't install an app or enter an OTP for one shop. Customers only get plain-text WhatsApp messages, with no live balance and no way to pay.

## Solution
Each customer gets a private, random link token. The owner puts the link in WhatsApp reminders, receipts and statements. The page `duesetu.com/k/<token>` reads one public endpoint: shop, balance, next due, the shop's UPI ID, recent entries. No login. The owner can reset the link at any time, which kills the old one.

## API Contract
- `GET /api/v1/owners/{ownerId}/customers/{customerId}/link` → `{ token, url }`. Makes a token on first call. Owner only, and the customer must belong to the owner.
- `POST /api/v1/owners/{ownerId}/customers/{customerId}/link/reset` → `{ token, url }` with a new token; the old link stops working at once.
- `GET /api/v1/public/k/{token}` (no login) →
  `{ shopName, shopPhone, shopImageUrl, customerName, balance, nextDue{amount, remaining, dueDate, status}|null, overdueCount, upiId|null, entries[{kind: SALE|PAYMENT|WRITE_OFF, label, amount, at}] (newest first, max 30), generatedAt }`.
  Unknown/reset token, deleted customer or deleted shop → 404 `NOT_FOUND`.

## Data Model
`customers.link_token` VARCHAR(32), unique, nullable (ddl-auto adds it). The token is 24 random bytes in base64url (192 bits), so it can't be guessed and needs no rate limit.

## Edge Cases
- The balance uses the same rule as the owner's customer page (sale total minus successful payments).
- Only successful payments are listed, never pending ones.
- The customer's phone is never in the public response.
- A sale with no product name gets a null label (the page shows "Sale").

## Out of Scope
"I've paid" from the page (owner decision 2026-11-11: later), link expiry, page analytics.
