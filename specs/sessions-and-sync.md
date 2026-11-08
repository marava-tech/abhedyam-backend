# Signed-in phones + live sync (multi-device step 2)

Plan: app repo `.product/FAMILY_ACCESS_PLAN.md`.

## Sessions
- Table `owner_sessions` (ddl update): owner_id, device_id (app install id, header `X-Device-Id`), device_name (`X-Device-Name`), last_seen_at, revoked_at.
- Google login creates one (or reuses the row of the same device); the owner JWT gets claim `sid`.
- `POST /auth/refresh`: session must not be revoked (else 401 `SESSION_REVOKED` → app shows sign in); updates last_seen. Tokens without `sid` (pre-1.13 / pre-deploy) get a session on their next refresh.
- JwtAuthenticationFilter: token whose `sid` is revoked → 401 `SESSION_REVOKED` (one PK lookup per request).
- `GET /owners/{id}/sessions` (current flag), `DELETE /owners/{id}/sessions/{sid}`, `POST .../logout-others`, `POST .../logout` (app Log out). Revoking deletes that device's FCM tokens.
- New phone signs in while another session is active → push "Signed in on a new phone" (category NEW_DEVICE, reminders channel) to the other phones.

## Live sync
- `SyncPushInterceptor`: after a 2xx POST/PUT/PATCH/DELETE by an owner token with a session (skips auth, fcm, notifications, feedback, admin, sessions, uploads), sends a data-only FCM `{type: sync}` (normal priority, collapse key `sync`, TTL 60 s) to the owner's other phones (FCM rows whose device_id ≠ the caller's `X-Device-Id`). Coalesced per owner+phone: one ping per 2 s.
- Apps before 1.13 ignore data-only messages (nothing shown).
