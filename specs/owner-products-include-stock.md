# Owner products include stock

## Problem
`GET /api/v1/owners/{ownerId}/products` returns product rows without
current stock. The stock page loads this list, then has to guess or
call `/stock/{productId}/current` per item. Shopkeepers see "—" instead
of how many are in the shop.

## Solution
Include `stock` on every product in that paginated list. Missing
inventory counts as 0.

## API Contract
`GET /api/v1/owners/{ownerId}/products`

Unchanged query params: `q`, `isActive`, `page`, `size`, `sortBy`,
`sortDirection`.

Each `content[]` item gains:

- `stock` (number) — current inventory for that product; `0` if none

Other product fields stay as they are.

## Data Model
None. Stock already lives on `inventories`.

## Edge Cases
Empty page: no inventory lookup. Product with no inventory row: `stock`
is `0`. Duplicate names on a page: same dedupe as today, stock is for
the kept row.

## Out of Scope
Changing `/products/owner/{id}/with-stock`. New stock write APIs.
