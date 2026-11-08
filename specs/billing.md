# Billing (trial → Play subscription → read-only) and referrals

Plan and research: `abhedyam-owner/.product/MONETIZATION_REDESIGN.md`.

## Access
- One date per owner: `owners.access_until`. Full access while it is in the future; after it the app is **read-only** (view, search, call, export, Payment in stay free; the app enforces this).
- Signup: `access_until = now + 30 days` (`BillingService.TRIAL_DAYS`). No card.
- `owners.play_product_id`: the Google Play product being paid for (null = not paying). `owners.first_paid_at`: first real payment.
- Prices live in Google Play / RevenueCat only: `abhedyam_yearly` ₹999/yr, `abhedyam_monthly` ₹129/mo, `abhedyam_founding_yearly` ₹499/yr (offering `founding`). Play products have **no free trial** (our trial is the backend one).
- Founding seats: `100 − count(first_paid_at not null)`, returned as `foundingLeft`. The app shows the founding offering while seats are left (soft limit).

## API
- `GET /api/v1/billing/me` → `{accessUntil, active, paying, playProductId, daysLeft, foundingLeft}`.
- `POST /api/v1/billing/revenuecat` (public): RevenueCat webhook. `Authorization` must equal `REVENUECAT_WEBHOOK_AUTH`; blank config = every call is rejected (401).
  - `INITIAL_PURCHASE`, `RENEWAL`, `PRODUCT_CHANGE`, `UNCANCELLATION`, `NON_RENEWING_PURCHASE`, `SUBSCRIPTION_EXTENDED` with `expiration_at_ms` → `access_until = max(access_until, expiry)`, `play_product_id = product_id`.
  - The same minus `SUBSCRIPTION_EXTENDED`, with `period_type != TRIAL`, is a payment; the first one sets `first_paid_at` and rewards the referrer.
  - `EXPIRATION` → `play_product_id = null` (access already ends at the expiry date).
  - Unknown/anonymous `app_user_id` → `{applied:false}`, 200.
- `GET /api/v1/referrals` → `{referralCode, shareLink (duesetu.com/join?ref=CODE), monthsEarned, cashEarned, usedACode, friends[{businessName, joinedAt, paid}]}`.
- `POST /api/v1/referrals/apply {referralCode}` (owner only): case-insensitive; not own code, not after paying, once per owner. Friend gets +15 days.
- Admin (`X-Admin-Key`): `POST /admin/owners/{id}/subscription/upgrade` = +365 days, `/downgrade` = access ends now.

## Referral reward
When the friend first pays: the referrer gets +30 days, or, if the referrer already pays on Play (free days would not stop Google charging), ₹200 by UPI (`referrals.reward_cash = true`, paid by hand). No activity, device or customer-count checks: a payment is the proof.

## Reminders
10:30 IST daily, owners not paying: push (category `BILLING`) at 3 days left, last day, and the day access ended. Dedupe key `access-end-<days>-<accessUntil epoch>`.

## Migration
`BillingResetMigration` (startup, idempotent): drops `owners.subscription/subscription_status/subscription_id/valid_till/device_hardware_id`, the `subscriptions` table, `SUBSCRIPTION` audits and the old referral score columns; makes enum columns VARCHAR; owners with no `access_until` get a fresh 30-day trial. Delete the class after it has run in production.
