# Architecture Decisions — Abhedyam Backend

## 2026-04-01 — MongoDB as primary DB (superseded)
**Superseded (checked 2026-11-08):** the code uses MySQL through JPA for all data; there is no MongoDB dependency or repository.
**Decision:** Use MongoDB for main business data (customers, products, sales, payments).
**Why:** Flexible document model fits the varied data structures of a khata book. Shopkeepers have different product types, payment modes, and customer data shapes.
**Alternatives considered:** MySQL only (too rigid for flexible product/customer data)
**Outcome:** Working well. JOIN operations needed for village-customer queries handled via native queries.

## 2026-04-01 — Interface + impl pattern for services
**Decision:** Every service has `IXxxService` interface + `XxxServiceImpl` implementation.
**Why:** Required from .cursorrules. Enables clean mocking in tests and clear contract definition.
**Outcome:** Consistent across all services.

## 2026-04-01 — Pagination default: page=0, size=20
**Decision:** All paginated endpoints default to page=0, size=20. Validated in service layer.
**Why:** Consistent UX across all list endpoints. Service layer guards against invalid values.
