# Learnings — Abhedyam Backend

> Accumulated lessons, gotchas, and what-not-to-do.
> Add an entry when you discover something surprising.

---

## Architecture & Design

- Always use interface + impl pattern for services (IXxxService + XxxServiceImpl). Spring DI works cleanly, easier to mock in tests.
- DTOs must never reach the service layer — only controllers handle DTOs. Services work with domain models.
- Use `PageResponse<T>` wrapper for all paginated endpoints. Clients expect `totalElements`, `totalPages`, `hasNext`, `hasPrevious`.
- When adding query filters that are optional (like `village`), always normalize first: trim + null check. Use conditional branching, not overloaded repo methods.

## Database

- MySQL uses a compound index only from its leftmost column. Add an `@Index` on columns queried alone, even if a compound index contains them.
- For village-specific customer filtering: exact match (case-insensitive, trimmed) with `village` query param vs generic search are different repository methods. Don't conflate them.
- LEFT JOIN in JPQL queries: use `searchCustomersWithVillageFilter()` pattern in repository when joining with LocationDetails.
- Redis is cache only — never use as primary data store. If Redis is down, the app should degrade gracefully.

## API Design

- Pagination was added to `/api/v1/location-details/villages` — previously returned all villages as a list. Response changed from `List<VillageResponse>` to `PageResponse<VillageResponse>`. Clients must handle this.
- All pagination defaults: page=0, size=20. Validate and normalize in service layer before calling repo.
- Shared endpoints (used by multiple clients): `/api/v1/files/upload`, `/api/v1/fcm/register`, `/api/v1/fcm/unregister`, `/api/v1/owners/{id}` — changes here affect all 3 clients.

## Configuration

- Never use default values in `@Value("${prop:default}")` for secrets (passwords, API keys). If the env var is missing, fail fast with a `@PostConstruct` check.
- All credentials via environment variables. Local dev uses .env file (gitignored).

## Development Rules (from .cursorrules)
- Implement only what's in the spec. No extra APIs or modules.
- No comments in generated code — readability through clean structure.
- Always update OpenAPI spec for public endpoints.
- Validate and sanitize all incoming data.
- Tests must run without external dependencies (mock gateways and APIs).
- Design payment/OTP endpoints with idempotency.

---

## 2026-04-01 — GitHub Actions must use Java 21
**Problem:** CI Maven build failed with `release version 21 not supported`.\n
**Solution:** Ensure GitHub Actions uses JDK 21 (Temurin) to match `pom.xml` (`<java.version>21</java.version>`).\n
**Why:** Maven compiler `--release 21` requires a Java 21 toolchain; JDK 17 runners cannot compile it.

## 2026-11-11 — Enum columns on MySQL are native `enum(...)`, and ddl-auto never widens them
**Problem:** Adding `AppLanguage.HI` would have failed in prod: Hibernate 6 created `fcm_details.language` as MySQL `enum('EN','TE')`, so saving `HI` gives "Data truncated". Tests (H2) showed nothing; `ddl-auto: update` adds columns but never changes an existing column's type.
**Solution:** `@JdbcTypeCode(SqlTypes.VARCHAR)` on the field (new databases get VARCHAR) plus a one-off startup step (`LanguageColumnWidening`) that ALTERs the prod column and drops a language CHECK if present (Hibernate adds `check (x in (...))` to VARCHAR enums too). Deployed with #78; the class can be deleted now.
**Why:** Before adding a value to any `@Enumerated(STRING)` field, check the prod column type (`information_schema.columns`). New enum fields: add `@JdbcTypeCode(SqlTypes.VARCHAR)` from the start.
