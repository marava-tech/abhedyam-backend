# Notifications in the owner's language (Telugu plan phase 4)

### Problem
Pushes and the in-app notification list are English text built in code ("Prava pays tomorrow", "Money to collect").
Owners using the app in Telugu read English notifications every day.

### Solution
- Notification texts live in `messages.properties` (English) and `messages_te.properties`, one key per kind,
  with plurals and dates handled per language.
- When a notification is made, it is written in **every** app language and stored with the row
  (`title`/`message` stay English; `texts` holds the others).
- **Push:** each phone gets the language it registered with its token (`fcm_details.language`, PR #65);
  unknown or old app = English.
- **In-app list:** shown in the language the app asks for (`Accept-Language` header; the app sends its language).
  Rows from before this change have only English.
- The other pushes (test notification, new phone signed in, family member joined) use the same texts.
- What the owner typed (reminder text, customer, village and item names, due notes) stays as typed.

### API Contract
No new endpoints. `GET /api/v1/notifications/**`: `title` and `message` follow `Accept-Language` (en, te; anything
else = English). Push payloads unchanged apart from the text.

### Data Model
`notifications.texts` TEXT NULL: JSON `{"te": ["title", "body"]}`. Added by ddl-auto.

### Edge Cases
- A language added to `AppLanguage` without a messages file: falls back to English (test fails first).
- Owner with two phones in different languages: each phone gets its own push.
- Deferred (quiet hours) pushes: sent in each phone's language from the stored texts.

### Out of Scope
- Customer-facing messages (SMS/WhatsApp reminders are written in the app).
- Translating old rows.
