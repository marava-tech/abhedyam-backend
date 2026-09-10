# Web customer OTP, payment links, public shops

Additive backend work for the CustomerDues web app. Android Connect keeps `POST /auth/phone/login` unchanged.

## OTP
- `POST /api/v1/auth/otp/send` `{ phone }`
- `POST /api/v1/auth/otp/verify` `{ phone, otp }`
- Redis: 6-digit OTP, 5 min, 5 attempts, 60s resend cooldown
- Rate limit `/api/v1/auth/**` 30 req / 15 min / IP

## Other
- `POST /api/v1/auth/refresh`
- PDF upload on `/files/upload`
- `POST /payment-links`, `GET /public/payment-links/{token}`
- Owner `publicSlug` + `publicListingEnabled`
- `GET /public/shops`, `GET /public/shops/{slug}`
- `app.subscription.enforce` default false
