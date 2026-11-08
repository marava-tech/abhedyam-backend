# API Guide — Abhedyam Backend

> All 91 actively-used endpoints across the 3 clients.
> Base URL: `https://abhedyam-backend.marava.tech/api/v1`
> Auth: Bearer token in Authorization header (JWT from Firebase + backend exchange)

## Abhedyam Owner App (65 endpoints)

### Authentication & Import
| Method | Endpoint | Notes |
|---|---|---|
| POST | `/auth/google/login` | Firebase token → backend JWT |
| GET | `/bulk-import/template` | Download Excel template |
| POST | `/owners/{ownerId}/bulk-import` | Import customers, sales, and payments from Excel |

### Products & Inventory
| Method | Endpoint |
|---|---|
| GET | `/owners/{ownerId}/products` | Paginated. Each item includes `stock`. |
| POST | `/products` |
| GET | `/stock/{productId}/current` |
| PUT | `/stock/update` |
| GET | `/image-store/search` |

### Customers & CRM
| Method | Endpoint |
|---|---|
| GET | `/owners/{ownerId}/customers` |
| GET | `/customers/{customerId}` |
| POST | `/customers` |
| GET | `/owners/{ownerId}/customers/{customerId}/summary` |
| GET | `/owners/{ownerId}/customers/{customerId}/sales-summary` |
| GET | `/owners/{ownerId}/customers/{customerId}/payments-summary` |
| GET | `/owners/{ownerId}/customers/{customerId}/notes-summary` |
| GET | `/owners/{ownerId}/customers/{customerId}/reminders-summary` |
| GET | `/location-details/customers/{customerId}` |
| GET | `/notes/customer/{customerId}` |
| GET | `/reminders/customer/{customerId}` |
| POST | `/notes` |
| POST | `/reminders` |
| GET | `/owners/{ownerId}/customers/{customerId}/sale-items` |
| PATCH | `/location-details/customers/{customerId}` |
| POST | `/location-details/customers/locations` |
| GET | `/location-details/villages` | Paginated. Params: name, page (default 0), size (default 20) |
| POST | `/customers/nearest` | PRO feature |

### Sales & Payments
| Method | Endpoint |
|---|---|
| POST | `/sales` |
| GET | `/owners/{ownerId}/payments` |
| GET | `/payments/customer/{customerId}` |
| POST | `/payments` |
| PATCH | `/payments/{paymentId}/status` |

### Stats & Dashboard
| Method | Endpoint |
|---|---|
| GET | `/stats` |
| GET | `/stats/recent-activities` |
| POST | `/stats/analytics` |
| GET | `/owners/{ownerId}/summary` |
| GET | `/stats/dashboard` |

### Profile & Business Settings
| Method | Endpoint |
|---|---|
| GET | `/owners/{ownerId}` |
| PATCH | `/owners/{ownerId}` |
| POST | `/files/upload` |
| GET | `/upi-accounts/owner/{ownerId}` |
| PATCH | `/upi-accounts/owner/{ownerId}` |
| POST | `/upi-accounts/owner/{ownerId}/verify` |
| GET | `/documents` |
| POST | `/documents` |
| PUT | `/documents/order` |
| GET | `/location-details/users/{userId}` |
| PATCH | `/location-details/users/{userId}` |

### Billing & referrals (specs/billing.md)
| Method | Endpoint |
|---|---|
| GET | `/billing/me` |
| POST | `/billing/revenuecat` (RevenueCat webhook, Authorization header) |
| GET | `/referrals` |
| POST | `/referrals/apply` |

### Notifications & Support
| Method | Endpoint |
|---|---|
| POST | `/fcm/register` |
| POST | `/fcm/unregister` |
| GET | `/users/{userId}/notifications` |
| GET | `/notifications/{id}` |
| PATCH | `/notifications/{id}/read` |
| POST | `/notifications/mark-read` |
| GET | `/invoices/customer/{customerId}` |
| GET | `/receipts/customer/{customerId}` |
| GET | `/feedbacks/user/{userId}` |
| POST | `/feedbacks` |
| GET | `/app-usage-guide` |

---

## Connect Customer App (15 endpoints)

| Method | Endpoint |
|---|---|
| POST | `/auth/phone/login` |
| POST | `/auth/logout` |
| GET | `/customers/me/summary` |
| POST | `/fcm/register` |
| POST | `/fcm/unregister` |
| GET | `/owners/{id}` |
| GET | `/owners/public` |
| GET | `/upi-accounts/owner/{id}` |
| GET | `/sale-items/customer/{id}` |
| GET | `/payments/customer/{id}` |
| POST | `/payments` |
| PATCH | `/payments/{id}/status` |
| POST | `/files/upload` |
| GET | `/documents` |
| GET | `/products/owner/{id}/with-stock` |

---

## Dashboard Admin Panel (11 endpoints)

| Method | Endpoint | Notes |
|---|---|---|
| GET | `/admin/owners` | Called twice — login validation + listing |
| GET | `/admin/owners/{ownerId}` | |
| POST | `/admin/owners/{ownerId}/subscription/upgrade` | |
| POST | `/admin/owners/{ownerId}/subscription/downgrade` | |
| GET | `/admin/trial-calls` | |
| POST | `/admin/owners/{ownerId}/calls` | |
| GET | `/admin/partners` | |
| POST | `/admin/partners` | |
| POST | `/admin/partners/{partnerId}/payout` | |
| GET | `/admin/feedbacks` | |
| GET | `/admin/image-store` | |
| POST | `/admin/image-store` | |
| PATCH | `/admin/image-store/{id}` | |
| POST | `/files/upload` | Shared with other clients |

---

## Shared Endpoints (used by multiple clients)

| Method | Endpoint | Used by |
|---|---|---|
| POST | `/files/upload` | Abhedyam, Connect, Dashboard |
| POST | `/fcm/register` | Abhedyam, Connect |
| POST | `/fcm/unregister` | Abhedyam, Connect |
| GET | `/owners/{ownerId}` | Abhedyam, Connect |
| GET | `/upi-accounts/owner/{ownerId}` | Abhedyam, Connect |
| GET | `/payments/customer/{id}` | Abhedyam, Connect |
| POST | `/payments` | Abhedyam, Connect |
| PATCH | `/payments/{id}/status` | Abhedyam, Connect |
| GET | `/documents` | Abhedyam, Connect |


## Session

| Method | Endpoint | Notes |
|---|---|---|
| POST | `/auth/refresh` | Fresh JWT from a still-valid Bearer token |

Customer sign-in is off: `/auth/phone/login` returns `OTP_REQUIRED` (phone alone proved nothing) until a replacement ships.

`POST /files/upload` now also accepts `application/pdf`.
