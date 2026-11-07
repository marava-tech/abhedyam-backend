# Inventory: items and stock movements (slice 1, backend)

Status: approved by owner 2026-11-07 (research: app repo `.product/INVENTORY_REDESIGN_RESEARCH.md`).

## Problem
Stock is one number per product that is overwritten. Sales that oversell silently bump it up to 0, items made in
New sale start at 0 so every sale "oversells", there is no history, no units, no low level, no edit/delete, and a
stock error inside a sale rolls the whole sale back (`recordSaleOut` is `@Transactional` inside the sale's
transaction, so the `try/catch` around it cannot save the sale).

## Model
- `products` gains (all nullable, old rows keep working):
  `unit` (`PCS` `KG` `G` `L` `ML` `BOX` `DOZEN`; null = `PCS`), `costPrice` (DECIMAL 15,2),
  `trackStock` (BIT), `lowStockLevel` (DECIMAL 18,3).
- New `stock_movements`: `ownerId, productId, type, quantity` (signed change, DECIMAL 18,3), `stockAfter`,
  `reason` (`DAMAGED` `USED` `RETURNED` `OTHER`, only for `REMOVED`), `note` (≤200), `saleTransactionId` + BaseEntity.
  Types: `OPENING` `RECEIVED` `REMOVED` `COUNT` (owner counted, quantity = difference) `SALE` `SALE_CANCELLED`.
- `inventories.stock` stays the running total. Every change writes the movement and the total in the same
  transaction, with the inventory row locked (`PESSIMISTIC_WRITE`) so two sales can't lose an update.
- Startup migration (safe to re-run): `trackStock IS NULL` → `true` when the product's stock ≠ 0, else `false`.

## Rules
- **Untracked items** (`trackStock` false/null): sales write nothing, the API returns `stock: null`, status `UNTRACKED`.
- **Tracked items**: a sale writes `SALE −qty`; stock **may go negative** (no more silent bump). Cancelling a sale
  writes `SALE_CANCELLED +qty`.
- **Stock never fails a sale**: the sale path calls a method that does its work in the sale's transaction (so a
  rolled-back sale also undoes its stock) but catches and logs its own errors instead of throwing.
- Turning tracking on with a count writes `OPENING` (no movements yet) or `COUNT`. Turning it off keeps the number
  and history, just hides them.
- `RECEIVED` / `REMOVED`: quantity > 0 (REMOVED may go below zero). `COUNT`: quantity ≥ 0 = the counted stock;
  a count equal to the current stock writes nothing. Any of these on an untracked item turns tracking on.
- Status: `OUT` stock ≤ 0 · `LOW` 0 < stock ≤ lowStockLevel · `OK` · `UNTRACKED`.
- Delete = archive (`isActive=false`): hidden from the list; history kept.
- Name required, ≤ 80; unique among the owner's active items (case-insensitive) → `ITEM_EXISTS`.
  Price ≥ 0. costPrice ≥ 0 or null. lowStockLevel > 0 or null (0 clears it). Quantities: 3 decimals.
  `code` is generated (`ITEM-XXXXXXXX`); owners never type it.

## API (owner only, own items) `/api/v1/owners/{ownerId}/items`
- `GET ?filter=ALL|LOW|OUT&search=&page=0&size=30` → `{ summary, items: PageResponse<Item> }`
  - `summary`: `{ total, tracked, low, out, stockValue, missingCostPrice }`; `stockValue` = Σ stock × costPrice over
    tracked items with stock > 0 and a cost price; `missingCostPrice` = how many such items have no cost price.
  - `Item`: `id, name, price, costPrice, unit, trackStock, stock, lowStockLevel, status, stockValue, imageUrl, updatedAt`.
  - Order: `OUT`, then `LOW`, then name.
- `POST { name, price, costPrice?, unit?, trackStock?, openingStock?, lowStockLevel?, imageUrl? }` → Item.
  `openingStock` given ⇒ tracked + `OPENING` movement.
- `PUT /{itemId}` same fields, only the ones sent change → Item. `trackStock:true` + `openingStock` = count.
- `DELETE /{itemId}` → archive.
- `POST /{itemId}/stock { type: RECEIVED|REMOVED|COUNT, quantity, reason?, note? }` → Item.
- `GET /{itemId}/movements?page=0&size=30` → `PageResponse<Movement>` newest first:
  `id, type, quantity, stockAfter, reason, note, saleTransactionId, createdAt`.

## Old endpoints (released apps keep working)
`PUT /stock/update` → `COUNT` (+ tracking on). `POST /stock/adjust` → `RECEIVED`/`REMOVED` by sign.
`POST /stock/purchase-in` → `RECEIVED`. `GET /stock/{id}/current` unchanged. `POST /products` (no stock field)
makes an untracked item; the released app then calls `/stock/update`, which counts and turns tracking on.
Products made from New sale → untracked.

## Not in this slice
New sale warnings, low stock in the morning digest (slice 3); app screens (slice 2); deleting old app screens and
unused endpoints (slice 4).

## Tests
Movements + totals for each type; sale of untracked writes nothing; tracked sale goes negative; cancel restores;
**a stock failure doesn't roll back the sale**; status/filter/summary/stockValue; name uniqueness; migration rule;
owner can't touch another owner's items.
