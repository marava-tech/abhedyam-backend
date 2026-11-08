# Multi-device phase 1: no lost changes

Full plan: app repo `.product/FAMILY_ACCESS_PLAN.md`. One owner (later family/helpers) on several phones.

1. **Owner settings merge** — `PATCH /owners/{id}/settings` merges `otherFlags` by key (a null value removes the key) instead of replacing the map. Old apps that send the whole map behave as before.
2. **Customer edit check** — `customers.edit_version` (BIGINT NOT NULL DEFAULT 0, added by ddl update) goes up on every `PUT /customers/{id}`. If the request carries `editVersion` and it differs: 409 `STALE_EDIT` "This customer was changed on another phone. Reload and try again". No `editVersion` = no check (old apps). Not JPA `@Version`, so background updates never fail.
3. **Payment in idempotency** — `POST /owners/{id}/payments/collect` takes `idempotencyKey`; the same key within 10 minutes records nothing and returns 200 with empty `payments`. Redis SETNX; a failed collect releases the key; Redis down = record anyway.
4. **jjwt 0.12.3 → 0.12.6** — 0.12.3 threw `NoSuchElementException` when many requests validated a token at once on first use (the "Session expired" right after a restart). `JwtUtilConcurrencyTest` failed ~1 in 3 runs before, 0 in 15 after.
