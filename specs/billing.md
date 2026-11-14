# Billing (trial → Play subscription → read-only) and referrals

Plan and research: `abhedyam-owner/.product/MONETIZATION_REDESIGN.md`.

## Access
- One date per owner: `owners.access_until`. Full access while it is in the future; after it the app is **read-only** (view, search, call, export, Payment in stay free; the app enforces this).
- Signup: `access_until = now + 30 days` (`BillingService.TRIAL_DAYS`). No card.
- **Earned second month:** every 15 min, trial shops (never paid, not paying, no bonus yet, access active) with ≥10 customers and ≥5 successful payments get +30 days once (`owners.trial_bonus_at`) and a BILLING push "You earned a second free month". Marketed as "2 months free" with that condition shown.
- `owners.play_product_id`: the Google Play product being paid for (null = not paying). `owners.first_paid_at`: first real payment.
- Prices live in Google Play / RevenueCat only, **yearly only** (no monthly, no lifetime prices): **Shop** `abhedyam_plan` ₹699/yr and **Business** `abhedyam_business` ₹1,299/yr; founding shops (first 100) get the **first year** at ₹349 / ₹649 (1-year introductory offer, RevenueCat offerings `founding` / `founding_business`). Play products have **no free trial** (our trial is the backend one). Decisions: app repo `.product/BUSINESS_PLAN.md`.
- Founding seats: `100 − count(first_paid_at not null)`, returned as `foundingLeft`. The app shows the founding offering while seats are left (soft limit).

## Plans and limits (`Plan`)
| | Shop (trial, Shop, read-only) | Business (`play_product_id` starts with `abhedyam_business`, access active) |
|---|---|---|
| Owner phones signed in at once | 1 | 10 |
| Family + helpers (joined or invited) | 2 | 10 |
- Invite past the limit → `PLAN_LIMIT`.
- `ShopMemberService.enforcePlan(owner)` runs at owner sign-in, after every RevenueCat event and after a member is removed: owner phones past the limit are logged out (most recently used stay; on Shop, signing in on a new phone logs the old one out); members past the limit (joined first, oldest first, then invites) are **paused** (`shop_members.paused_at`, sessions revoked, nothing deleted; sign-in/refresh → `PLAN_PAUSED`), invites past it are cancelled; members back within the limit are unpaused.

## API
- `GET /api/v1/billing/me` → `{accessUntil, active, paying, playProductId, daysLeft, foundingLeft, plan (SHOP|BUSINESS), maxPhones, maxHelpers, extraMonthEarned}`. Members list rows carry `paused`.
- `POST /api/v1/billing/revenuecat` (public): RevenueCat webhook. `Authorization` must equal `REVENUECAT_WEBHOOK_AUTH`; blank config = every call is rejected (401).
  - `INITIAL_PURCHASE`, `RENEWAL`, `PRODUCT_CHANGE`, `UNCANCELLATION`, `NON_RENEWING_PURCHASE`, `SUBSCRIPTION_EXTENDED` with `expiration_at_ms` → `access_until = max(access_until, expiry)`, `play_product_id = product_id`.
  - The same minus `SUBSCRIPTION_EXTENDED`, with `period_type != TRIAL`, is a payment; the first one sets `first_paid_at` and rewards the referrer.
  - `EXPIRATION` → `play_product_id = null` (access already ends at the expiry date).
  - Unknown/anonymous `app_user_id` → `{applied:false}`, 200.
- `GET /api/v1/referrals` → `{referralCode, shareLink (duesetu.com/join?ref=CODE), monthsEarned, usedACode, friends[{businessName, joinedAt, paid}]}`.
- `POST /api/v1/referrals/apply {referralCode}` (owner only): case-insensitive; not own code, not after paying, once per owner, and only a shop that joined **after** the code's owner (`REFERRER_NOT_OLDER`), so two shops can't swap codes for bonus days. Friend gets +15 days.
- Admin (TOTP admin token): `POST /admin/shops/{id}/access {days}` adds days, `/access/end` ends access now. See `admin-dashboard.md`.

## Referral reward
When the friend first pays: the referrer always gets +30 days, never money, also when they already pay on Play (the days extend `access_until`; a later Play renewal event can replace it with Play's own expiry if that is later). `referrals.reward_cash` is always false now; the old cash columns and admin cash-paid endpoint are dead code to delete. No activity, device or customer-count checks: a payment is the proof.

## Reminders
10:30 IST daily, owners not paying: push (category `BILLING`) at 3 days left, last day, and the day access ended. Dedupe key `access-end-<days>-<accessUntil epoch>`.

## Founder calls (admin)
`GET /api/v1/admin/trial-calls`: non-paying shops from the last 60 days that are due a call. Stage (day 1 = signup day): `ENDED` (access ended ≤7 days ago), `ENDING` (≤3 days left), `DAY_3` (days 3–5), `DAY_20` (days 20–22). Row: name, shop, phone, email, trial day, days left, customers, sales, `calledAt` for this stage, latest note. Not-yet-called first.
`POST /api/v1/admin/owners/{id}/calls {stage, note}` logs a call (`founder_calls`).

## Partners (admin)
Agents / accountants / distributor salesmen. `partners` (name, phone, UPI id, code `ABH…`, same namespace as owner codes). A shop that uses a partner code gets the same +15 days; `owners.partner_id` is set (a shop has either a referrer or a partner, never both). On the shop's first Play payment: `partner_earnings` row, ₹250 (`ReferralService.PARTNER_RUPEES`), one per shop.
`GET /api/v1/admin/partners` (joined, paid, ₹ owed, ₹ paid), `POST /api/v1/admin/partners {name, phone, upiId}`, `POST /api/v1/admin/partners/{id}/payout` (after paying by UPI: marks all unpaid as paid, returns ₹ total).
