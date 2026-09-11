# Always resolve payment names

## Problem
`GET /api/v1/owners/{ownerId}/payments` only filled `customerName` and
`productName` when the client passed `?expand=names`. The web app and the
Flutter owner app often omit that flag, so the payments list shows blank
names. The lookups are already three batched `findByIdIn` calls for the
whole page, not one per row, so the flag was not saving work.

## Solution
Always resolve customer and product names on the owner payments list.
`?expand=names` is ignored if still sent (unknown query params are dropped).

## API Contract
Unchanged response shape. `expand` is no longer read.

## Data Model
None.

## Edge Cases
Empty page: no lookups. Missing customer or product: name stays null.

## Out of Scope
Changing other list endpoints.
