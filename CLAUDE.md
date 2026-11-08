# Abhedyam Backend

## What this is
Digital khata book API for small Indian shopkeepers. Java 21 + Spring Boot 3.
MySQL 8 (primary DB) + Redis 7 (cache only).
CI/CD: GitHub Actions → ghcr.io/marava-tech/abhedyam-backend:latest → auto-deploy on merge to main.

## Product context
Abhedyam helps small shopkeepers manage customers, sales, inventory, and payments digitally. Owners sign in with Google (Firebase); customer sign-in is currently off. Billing: 30-day free trial, then a Google Play subscription via RevenueCat (one plan; read-only after access ends) — see specs/billing.md. Push notifications via Firebase FCM.

## Stack
- Java 21, Spring Boot 3.x
- MySQL 8 (primary DB — customers, products, sales, payments, referrals)
- Redis 7 (cache only — never primary store)
- Firebase Admin SDK (auth token verification)
- RevenueCat webhook (Google Play subscriptions)
- Gmail SMTP (account export emails)
- Port: 8600 → domain: abhedyam-backend.marava.tech

## Project structure
```
src/main/java/com/abhedyam/
├── controller/     ← REST controllers (@RestController)
├── service/        ← business logic (@Service, interface + impl pattern)
├── repository/     ← Spring Data JPA repos
├── model/          ← DB entities
├── dto/            ← request/response DTOs
├── exception/      ← custom exceptions (ResourceNotFoundException, etc.)
└── config/         ← Spring config (Security, CORS, Cloudinary, etc.)
```

## Agents — load before acting
- New feature idea → read .claude/agents/spec.md first
- Writing/fixing code → read .claude/agents/backend.md first
- Testing → read .claude/agents/qa.md first
- Git/PR → read .claude/agents/git.md first
- Server/Docker → read .claude/agents/devops.md first

## Workflow (always follow this order)
1. Spec agent writes specs/{feature}.md
2. Backend agent implements from spec
3. QA agent writes and runs tests
4. Git agent: branch → commit → push → PR
5. Merge PR → GitHub Actions auto-deploys. Never deploy manually.

## Hard rules
- Never push directly to main
- Never deploy manually
- Every feature needs a spec file before code is written
- All API routes under /api/v1/
- Error format: `{ "error": "message", "code": "ERROR_CODE" }`
- DTOs only in controllers — never expose model/entity directly
- Redis is cache only — never use as primary store
- Always add OpenAPI annotations to public endpoints
- Use interface + impl pattern for services (IXxxService + XxxServiceImpl)
- No hardcoded credentials — all from environment variables via @Value

## Related docs
- docs/api-guide.md — all 91 API endpoints across 3 clients
- docs/architecture.md — system design decisions
- learnings.md — gotchas and lessons learned
- memory/context.md — current focus / active work
- specs/ — feature specifications
