# Disable phone-number-only customer login

## Problem
`POST /api/v1/auth/phone/login` accepted just `{ phone }` and returned a 30-day
customer JWT (creating the customer if missing). No proof of phone ownership, so
anyone could sign in as any customer or mass-create customer accounts. The
endpoint is public (`/api/v1/auth/**`).

## Solution
The endpoint now always fails with `OTP_REQUIRED`. The old body is removed.
- Only consumer was the customers Flutter app, which has no users yet and is
  optional; it will show the error until it gets a verified-login flow.
- `/auth/otp/send` + `/auth/otp/verify` (web customer login) are unchanged.

## Follow-up (not in this change)
Free replacement for SMS OTP (SMS is too costly): an owner-issued, single-use,
expiring access code the customer enters with their phone number.

## Tests
`AuthServicePhoneLoginTest`: the call is rejected and touches no dependency.
