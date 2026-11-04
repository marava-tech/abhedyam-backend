# Notifications engine (owner app) — spec

Status: DRAFT for review. No code until the "Decisions" below are answered.

## Problem
Notifications grew by accident: two producers call `NotificationService` and `FcmService`
directly, there are no owner preferences, no quiet hours, no dedupe, and the only
schedule is a 60 s poll over *reminders*. An owner cannot say "stop pinging me at night"
or "only tell me when money moves", and a double trigger sends two pushes.

## What exists today (read from code, 2026-11-04)
Backend
- `notifications` table (`Notification`): owner, user, type (INFO / WARNING / ACTION_REQUIRED),
  message, isRead, relatedEntityId/Type, `retryCount` (set to 0, never used).
- Producers (only two):
  1. `ReminderSchedulerService.processDueReminders` (`@Scheduled` every 60 s): for IN_APP reminders
     creates a row and pushes to the *customer* (customer app) or the owner, or **broadcasts to all
     users of a type** when the reminder has neither.
  2. `PaymentService.sendPendingPaymentNotificationToOwner`: customer paid, owner must verify
     (ACTION_REQUIRED, row + push).
- `FcmService`: token register/unregister, send to one user, send to all users by type.
- `NotificationService.getAll()` returns `findAll()` — every tenant's rows (verify it is not exposed).
App
- `FcmService` (init, permission, foreground/opened handlers), `NotificationPrimer` (asks permission
  at a useful moment, max 2 asks), `NotificationProvider` (list + unread count),
  `notifications_screen.dart` (540 lines), `NotificationType` enum.
- More has a Notifications row (count only). No settings.

## Goals
1. A small, fixed set of **events**, each with a clear reason to exist (help collect money).
2. **One entry point** to notify: `NotificationDispatcher.notify(...)`. Nobody calls FCM directly.
3. Owner control: per-category switch, quiet hours, daily digest time.
4. No duplicate or night-time pushes; stale device tokens cleaned up.
5. Delete the old paths.

## Events (v1, owner-facing)
| Key | When | Push? | Default |
|---|---|---|---|
| `PAYMENT_NEEDS_OK` | a customer paid via link/app and needs the owner's OK | yes | on |
| `PAYMENT_RECEIVED` | a payment is confirmed (customer pay link settled) | yes | on |
| `DUE_DIGEST` | once a day at the owner's digest time, only if something is due: "7 customers owe ₹1,24,000 · oldest 45 days" | yes (1/day max) | on, 08:00 |
| `OWNER_REMINDER` | a reminder the owner created fires at its time | yes | on |

Not in v1 (decide later with data): weekly summary, "customer crossed 30 days" (folded into the digest
instead of its own push), marketing/product news, anything addressed to customers.
Customer-app reminders keep working but are out of this engine's scope (customer app is optional,
off by default).

Every event also writes an in-app row (the list), regardless of push settings.

## Quiet hours
- Owner setting: start/end, default **21:00–08:00**, timezone default `Asia/Kolkata`
  (store IANA tz per owner so it is correct later).
- A push due inside quiet hours is **deferred to the quiet-hours end** (not dropped). The in-app row
  is created immediately.
- Exception: none in v1 (even PAYMENT_NEEDS_OK waits; the morning push + in-app row is enough for a
  village shop). Revisit if owners ask.
- `DUE_DIGEST` is scheduled by its own time; if that time falls in quiet hours it is moved to the end.
- Pure function `QuietHours.nextSendTime(now, start, end, zone)` — unit tested (wrap past midnight,
  start==end meaning "off", DST-less zone, boundary instants).

## Opt-in
- **System permission:** unchanged: `NotificationPrimer` asks at a useful moment, max 2 asks.
  No push is attempted without a registered token.
- **Categories:** switches in More → Notifications. Defaults above.
- **Opt-out is honoured server-side** (dispatcher checks preferences), not just hidden in the app.

## Design
```
producer (PaymentService, ReminderScheduler, DigestJob)
   └─ NotificationDispatcher.notify(ownerId, category, title, body, entity, dedupeKey)
        1. write notifications row (category, dedupeKey UNIQUE per owner -> skip duplicates)
        2. load preferences; if category off -> row only (deliveryStatus SKIPPED)
        3. pushAfter = QuietHours.nextSendTime(now, ...)
        4. now >= pushAfter ? send via FcmService : deliveryStatus DEFERRED
   └─ PushDeliveryJob (@Scheduled 60 s, reuses the existing poll): send DEFERRED rows with pushAfter <= now
```
Schema (Flyway/ddl as the project does it)
- `notifications`: add `category VARCHAR`, `dedupe_key VARCHAR` (unique with owner_id), `delivery_status`
  (SENT / DEFERRED / SKIPPED / FAILED), `push_after TIMESTAMP`. Drop `retry_count`.
- `notification_preferences`: `owner_id`, `category`, `push_enabled` (PK owner+category).
- `owners` (or a small `owner_settings`): `quiet_start`, `quiet_end`, `timezone`, `digest_time`.
API
- `GET/PUT /api/v1/owners/{ownerId}/notification-settings` -> `{ categories: {KEY: bool}, quietStart, quietEnd, timezone, digestTime }`.
- Existing list/mark-read endpoints stay; response adds `category`. Deep-link data stays
  `relatedEntityType/Id` (PAYMENT -> Collected "needs OK"; CUSTOMER -> customer detail).
FCM hygiene
- On `UNREGISTERED` / `INVALID_ARGUMENT` from FCM delete that token. Unregister the device token on
  logout and account delete (verify account delete already does).

## App
- More → Notifications row opens a **Notifications** screen with two parts: the list (existing,
  grouped by day, "Mark all read") and a **Settings** section: category switches, quiet hours, digest time.
- Tapping a push or a row deep-links by `relatedEntityType`.
- One place initialises FCM; `NotificationProvider` loads the list once and derives the unread count
  (today it fetches the list a second time just to count).
- Analytics: `notification_setting_changed{key,value}`, `notification_opened{category}`,
  `push_permission_result`.

## Remove (dead / risky)
Done in slice 1 (verified no callers):
- Broadcast to all users: `ReminderSchedulerService.sendBroadcastNotifications` / `sendNotificationToAllUsers`,
  `FcmService.sendNotificationToAllUsersByType` (also did a full `fcm_details` table scan) and the unused
  `sendNotificationToUsers`. Unreachable: reminders always get the current owner as `ownerId`, and the
  scheduler returns early when it is null.
- `NotificationService.getAll()` (`findAll()`, every tenant) — had no callers, so it was dead code, not an
  exposed endpoint. Also removed `getByOwnerId`, `getByUserId`, `update` (no callers).
Deferred to the schema slice (changes the API/DB): `Notification.retryCount` (still in `NotificationResponse`).

## Reminder channels (resolved)
Reminders used to accept EMAIL / SMS / WHATSAPP but only IN_APP was ever delivered (others were marked SENT and
logged). Removed: `ReminderChannel` is now `IN_APP` only; `channel` is optional in the request (older apps
still send `IN_APP`); legacy rows read back as IN_APP via `ReminderChannelConverter`. Real SMS/WhatsApp
sending, if wanted, is a separate product decision (reminders to customers are sent by the owner from their
own phone via the Remind sheet, never auto-sent).

## Build slices (each its own branch + PR)
1. Backend: delete dead paths (S; behaviour-neutral) — DONE on `chore/notifications-cleanup`.
2. Backend: dispatcher, `QuietHours`, preferences + settings API, deferred-push job (M) — DONE on `feat/notification-dispatcher` (muted categories live in an element-collection table instead of a `notification_preferences` table; the deferred job is a method on the dispatcher, not a separate class).
3. Backend: move `PAYMENT_NEEDS_OK` and `OWNER_REMINDER` onto the dispatcher (S).
4. Backend: `DUE_DIGEST` job + `PAYMENT_RECEIVED` (M).
5. App: Notifications screen settings + deep links + single FCM init (M).
6. App: remove the old unread-count double fetch and unused code (S).

## Tests
- Unit: `QuietHours.nextSendTime`, dedupe, preference gating, digest text (pure).
- Integration (H2): dispatcher writes a row, defers inside quiet hours, `PushDeliveryJob` sends once
  at the end, FCM invalid token is removed (FCM mocked).
- Never call real FCM or send to real devices in tests.

## Decisions needed (defaults in bold)
1. Event list for v1 as above (4 events)? **Yes**
2. Quiet hours default 21:00–08:00 and defer (not drop)? **Yes**
3. Daily digest on by default at 08:00 once push permission is granted? **Yes** (alternative: off until opted in)
4. Customer-facing notifications stay out of scope? **Yes**
5. Start with slice 1 (cleanup + security scoping)? **Yes**

## Risks
- Deferring pushes means an owner may see a payment at 08:00 instead of 23:00; acceptable for now.
- Broadcast removal could break an admin workflow: grep and ask before deleting.
- `findAll()` may already be unreachable; confirm before calling it a vulnerability.
