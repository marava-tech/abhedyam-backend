# Family & helpers (multi-device step 3)

Plan + decisions: app repo `.product/FAMILY_ACCESS_PLAN.md`.

## Model
- `shop_members`: owner_id (the shop), name, role FAMILY|HELPER, status INVITED|ACTIVE|REMOVED, invite_code (6 chars, no 0/O/1/I), invite_expires_at (48 h), email + firebase_uid (set on join), joined_at, removed_at.
- A member has no `users` row. Their JWT: sub = **shop owner id** (so `SecurityUtil.getCurrentUserId()` and every owner check work unchanged), plus `sid`, `mid` (member id), `role`.
- `owner_sessions.member_id`: which person a phone is signed in as (null = owner).
- `added_by` on every table (BaseEntity, filled on insert from the token's `mid`; null = owner). Async audit tasks copy the security context.

## Flows
- Owner: `POST /owners/{id}/members {name, role}` → invite (code, link `https://marava.tech/join/<code>`, expires 48 h). `GET` list (pending + active, last seen). `PATCH /{mid} {role}`. `DELETE /{mid}` → REMOVED, all their sessions revoked + push tokens dropped (instant).
- Public: `GET /public/invites/{code}` → shop name, member name, role, valid.
- `POST /auth/google/join {idToken, code}` → one Gmail = one shop: refused if that Gmail is an owner or an active member elsewhere; activates the invite (single use), session, token. Owner gets push "Ravi joined your shop as Helper".
- `POST /auth/google/login`: an active member's Gmail logs in as that member (checked before creating a new owner). `POST /auth/refresh`: member must still be ACTIVE (else 401 SESSION_REVOKED); role re-read.

## Roles (RoleGuardInterceptor, 403 NOT_ALLOWED)
- Owner only (Family and Helper refused): shop details `PATCH /owners/{id}`, settings, notification settings, UPI writes, members, sessions (except `logout`), account delete/export, subscription, bulk import, documents writes, owner location.
- Helper also refused: every PUT/PATCH/DELETE except notifications; POST only for new sale, Payment in (`/payments/collect`), add customer, add due, notes, reminders, chase-contacted, uploads, fcm, notifications, sessions/logout, feedback, analytics, nearest/locations queries.
- ponytail: helper GETs are not blocked on the server (totals/reports are hidden in the app); block them if helpers outside the family appear.

## Called today
- `POST /owners/{id}/settings/chase-contacted {customerId}`: adds to `otherFlags.chaseContacted` for today on the server (atomic per request; any role).

## Display
- `RecentActivityResponse.addedBy` and `PaymentResponse.addedByName`: member name ("by Ravi"); null = owner.
