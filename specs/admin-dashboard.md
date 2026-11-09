# Admin dashboard API (v2)

The founder's dashboard (`abhedyam-dashboard`) on the billing model in `billing.md`. All routes need the TOTP admin token (`SecurityConfig`, `UserPrincipal.isAdmin()`).

## Shop status (one per shop, first match wins)
| Status | Rule |
|---|---|
| `CLOSED` | account disabled (`users.is_active = false`, deleted after the 48 h grace) |
| `DELETING` | owner asked to delete (`owner_settings.deletion_requested_at` set) |
| `PAYING` | `owners.play_product_id` set |
| `TRIAL` | `access_until` in the future |
| `READ_ONLY` | `access_until` passed (or missing) |

## Endpoints
- `GET /api/v1/admin/overview` →
  - `shops {total, trial, paying, readOnly, deleting, closed}`
  - `payingByProduct {<playProductId>: count}` (the dashboard turns these into ₹/month with Play's prices)
  - `foundingLeft`, `signupsToday`, `signups7d`, `signups30d`, `signupsByDay [{date, count}]` (last 30 IST days, oldest first)
  - `activeShops7d`: shops with a sale or payment entered in the last 7 days
  - `trialsEnding3d`: trial shops with ≤ 3 days left, `callsDue`: trial-call rows not yet called
  - `funnel30d {signedUp, addedCustomer, madeSale, paid}`: shops that signed up in the last 30 days
  - `owed {partnersRupees, referralCashRupees}`
- `GET /api/v1/admin/shops?q=&status=&sort=&page=&size=` → page of `ShopRow`
  - `q`: name, shop name, email or phone (contains, any case). `status`: one of the statuses. `sort`: `newest` (default), `lastSeen`, `customers`, `daysLeft`.
  - `ShopRow`: id, name, businessName, email, phone, createdAt, status, accessUntil, daysLeft, playProductId, firstPaidAt, customers, sales30d, lastSeenAt (newest `owner_sessions.last_seen_at`), lastEntryAt (newest sale or payment), source (`{type: REFERRAL|PARTNER, id, name}` or null).
- `GET /api/v1/admin/shops/{id}` → `ShopRow` fields plus: totals (customers, sales, payments), `collected30dRupees` (confirmed, not written off), `setup` (`otherFlags.business` answers), `villageMode`, `locationTools`, `deletionRequestedAt`, `devices` (active sessions: name, member name, lastSeenAt), `members` (name, email, role, status, joinedAt), `referredFriends` (shop, joinedAt, paid), `calls` (stage, note, createdAt), `feedback` (category, text, imageUrl, createdAt).
- `POST /api/v1/admin/shops/{id}/access {days}` → adds 1–3650 days (from the later of now and the current end). Replaces the old `/subscription/upgrade`.
- `POST /api/v1/admin/shops/{id}/access/end` → access ends now (read-only). Replaces `/subscription/downgrade`.
- `GET /api/v1/admin/referrals` → newest first: id, referrer `{id, name}`, friend `{id, name}`, status (`JOINED`/`PAID`), joinedAt, rewardedAt, rewardCash, cashRupees (₹200 when `rewardCash`), cashPaidAt.
- `POST /api/v1/admin/referrals/{id}/cash-paid` → marks a ₹200 reward as paid by UPI (`referrals.cash_paid_at`). 409 `NO_CASH_REWARD` when the reward was days, not cash.
- Unchanged: `GET /admin/trial-calls`, `POST /admin/owners/{id}/calls`, `GET|POST /admin/partners`, `POST /admin/partners/{id}/payout`, `GET /admin/feedbacks`.

## Removed
- `GET /admin/owners`, `GET /admin/owners/{id}`, `POST /admin/owners/{id}/subscription/upgrade|downgrade` (replaced above).
- Image store: `GET|POST /admin/image-store`, `PATCH /admin/image-store/{id}`, `GET /image-store/search` (the app no longer uses them). The `image_store` table and entity stay only so account deletion never deletes shared catalog pictures that old products may still point to.

## Scale
Shop list and overview load every owner and use grouped counts (no per-shop queries). Fine to a few thousand shops; move filters into SQL after that.
