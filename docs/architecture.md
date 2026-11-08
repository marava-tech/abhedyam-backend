# Architecture — Abhedyam Backend

## System overview

Three client apps → Spring Boot REST API → MySQL (all data) + Redis (cache)

Clients:
- Abhedyam Flutter app (owner-facing) — Firebase JWT auth
- Connect Flutter app (customer-facing) — sign-in currently off
- Dashboard React/Vite (admin) — authenticator (TOTP) code

## Authentication
- Owner app: Firebase Google Sign-in → Firebase token → backend exchanges for JWT
- Customer app: off. `/auth/phone/login` returns `OTP_REQUIRED` (a phone number alone proved nothing) until a replacement ships
- Admin dashboard: TOTP code (`/auth/admin/login`) → backend issues JWT
- All JWTs validated on every request via Spring Security filter

## Package structure pattern
```
controller/ ← receives HTTP, validates input, delegates to service, returns DTO
service/    ← business logic, interface (IXxxService) + impl (XxxServiceImpl)
repository/ ← data access only, no business logic
model/      ← DB entity classes
dto/        ← request/response shapes (never pass entities through controller boundary)
exception/  ← domain exceptions (ResourceNotFoundException, etc.)
config/     ← Spring beans (SecurityConfig, CorsConfig, CloudinaryConfig, etc.)
```

## Database design decisions
- **MySQL for everything**: customers, products, sales, payments, subscriptions, stats, audit logs (JPA, `ddl-auto=update`: it adds columns but never drops them)
- **Redis as cache only**: dashboard stats, rate limiting. Never store business data here.
- Sum money in SQL (`GROUP BY`), never by loading every sale line or payment into Java

## Key design patterns
- **Interface + impl for services**: Every service has `IXxxService` interface and `XxxServiceImpl`. Enables easy mocking in tests.
- **`PageResponse<T>` wrapper**: All paginated endpoints return this with `totalElements`, `totalPages`, `hasNext`, `hasPrevious`
- **`ApiResponse<T>` envelope**: All responses wrapped for consistent structure
- **Idempotency for payments**: payment verify is safe on retry

## Subscriptions
Three tiers: GO (free), PRO, PLUS. Payments via Razorpay. Subscription state checked on feature access.

## File uploads
Files go to MinIO storage via the upload-api service (https://s3.marava.tech/upload). Abhedyam backend doesn't handle file storage directly.
