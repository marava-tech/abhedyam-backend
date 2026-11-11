# Follow-ups ("Call again on…")

Product plan: abhedyam-android `.product/REMINDERS_PLAN.md` (step 2). The old Reminders tool is gone from the app; "reminder" now only means a WhatsApp/SMS the owner sends a customer. A follow-up is the owner's own alert: "call this customer again on this day".

## Behaviour
- Created for one of the owner's customers with a **date** (today or later) and an optional note (≤ 200 chars).
- Pushed to the owner at **10:00 in the owner's time zone** on that day (`ReminderSchedulerService`): title "Call {name} again" (en/te), body = note or "You planned to call them today". Entity `REMINDER` + id, so the app opens Upcoming collections on that row. If 10:00 that day has already passed when it is set or moved, no push (the owner is in the app).
- Listed in `GET /owners/{ownerId}/dues` as `followUps` (same window as dues/visits; `OVERDUE` once the day passed) until marked done or deleted. Rows whose customer was deleted are dropped.
- Moving to a new date schedules a new push (dedupe key includes the time).

## API (owner token, own shop only)
| Method | Path | Body |
|---|---|---|
| POST | `/api/v1/owners/{ownerId}/customers/{customerId}/follow-ups` | `{ date, note? }` |
| PUT | `/api/v1/owners/{ownerId}/follow-ups/{id}` | `{ date?, note?, done? }`; `done: true` → data null (left the list) |
| DELETE | `/api/v1/owners/{ownerId}/follow-ups/{id}` | — |

`FollowUpResponse`: id, customerId, customerName, customerPhone, village, date, note, status (`UPCOMING` / `OVERDUE`).

## Storage
Rows in `reminders` with `type = FOLLOW_UP`, `time` = the push instant, `text` = note.
- open: `status` PENDING (push to come) or SENT, `is_active = true`
- done: `status = SENT`, `is_active = false`
- deleted: `status = DELETED`, `is_active = false`

No new enum value: Hibernate 6 may have created a CHECK constraint on `reminders.status`, and `ddl-auto: update` does not widen it.

## Morning digest (step 3)
- Adds "N to call again" for follow-ups due today or overdue.
- When money is due today or late, the digest title is "Send reminders for today" (en/te/hi) and the entity is `DIGEST_DUES`, so new apps open Upcoming collections (Message / Remind all); older apps route by category (`DUE_DIGEST`) and open Customers as before. Collection-cycle days are already counted in "dues today".
- `notif.followup.*` in Hindi (was missing; Hindi owners got English).
