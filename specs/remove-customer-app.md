# Remove the customer app (Abhedyam Connect)

## Problem
The customer app (Play `tech.marava.abhedyamc`) is retired (owner, 2026-11-11). Customers open their khata from a private link instead (`specs/customer-khata-link.md`). The backend still carried the app's sign-in, tokens, endpoints and access branches. One leftover was a live bug: every reminder with a customer attached was pushed to the customer app, so the owner's Reminders tool (which always attaches a customer) never reached the owner.

## Solution
- Remove `POST /auth/phone/login` (already always OTP_REQUIRED) and `PhoneLoginRequest`.
- `POST /auth/refresh` no longer renews customer tokens. `JwtUtil.generateCustomerToken` and `app.jwt.customer-expiration` are gone.
- The JWT filter accepts only active owners (family/helper tokens carry the owner id) and admin. An old customer token now gets 401 `USER_NOT_FOUND`.
- Remove `GET /customers/me/summary`, `GET /owners/public` (+ permitAll), `/inventories/**` (its `owners/*` GET was permitAll), and `/sale-items/{id}` + `/sale-items/customer/{id}`. Neither the owner app nor the dashboard calls any of them.
- Payments, documents and UPI lookup are owner-only (the "caller is the customer" branches are gone). A document list for another shop's `ownerId` is refused instead of returning its customer-visible documents.
- Reminders: every reminder goes to the owner through the notification dispatcher (OWNER_REMINDER), with or without a customer. `ReminderCreateRequest.packages` and `PackageConstants.CUSTOMER_APP_PACKAGE` are removed.
- The customer-made pending payment push (`PAYMENT_NEEDS_OK` sender, `notif.payment.*` texts) is removed. The enum value stays because stored notification rows use it.

## Kept on purpose
- `UserType.CUSTOMER` and customer rows: customers are still records.
- `Document.visibleToCustomers` in the response: the released owner app reads it. Drop it after the app no longer does.

## Out of Scope
Removing unused InventoryService methods, the `reminders.packages` column, and `owners.public_slug` / `public_listing_enabled`.
