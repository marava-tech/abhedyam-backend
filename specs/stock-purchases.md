# Purchases (stock bought in one go)

Status: owner asked 2026-11-07 ("businesses buy products in loads ... price paid for the load, when, how many,
how many are left, visually easy, no extra typing"); decisions taken by product (below). Builds on
`inventory-items.md`.

## Need
A shopkeeper buys stock from a wholesaler: one bill, several items, one total. They want to see, per purchase, what
they paid, when, how much came, how much is left, and whether it has paid for itself, without typing a buying
price for every item.

## Decisions (and why)
- **Term: "Purchase"** (UI: Purchases, New purchase, "Bought from …"). Khatabook, Vyapar, myBillBook and OkCredit all
  say purchase / purchase bill, so shopkeepers know it from their wholesaler bills, and it pairs with "Sale" already
  in the app. "Lot"/"batch" mean expiry/traceability codes in inventory software; "load" sounds like transport.
- **One number**: the owner types the total paid. The cost is split across the purchase's items by **relative sales
  value** (each item's share of `quantity × selling price`), the standard way to cost a basket purchase. An exact
  per-item cost can be given instead; the remaining total is split over the rest.
- **No typing to know what is left**: sales and removals use up stock **oldest first (FIFO)**, so each purchase's
  "left" is computed from the stock history on every read. Nothing is stored that can drift.
- **Buying prices fill themselves**: saving a purchase sets each item's `costPrice` to its unit cost from this purchase,
  so stock value and profit work without the owner ever entering a buying price.
- **Delete only while untouched**: a purchase can be deleted until any of it has been sold or removed (fixes typos
  without rewriting history). Deleting writes a matching `REMOVED` movement; both are ignored by FIFO.
- "Paid back" uses current selling prices (an estimate; labelled "about" in the app).

## Model
- `stock_purchases`: `ownerId, supplier (≤60, optional), boughtOn (date, default today, not in the future),
  totalPaid (15,2), note (≤200)` + BaseEntity (`isActive=false` = deleted).
- `stock_purchase_items`: `purchaseId, ownerId, productId, quantity (18,3) > 0, unitCost (15,4), costGiven (bool)`.
- `stock_movements.purchaseId` (nullable): the `RECEIVED` movement of each purchase item, and the reversal on delete.

## Create `POST /api/v1/owners/{ownerId}/purchases`
`{ supplier?, boughtOn?, totalPaid?, note?, items: [{ itemId? | newItem: {name, price, unit?}, quantity, unitCost? }] }`
- ≥ 1 item, no item twice. `newItem` creates the item (same rules as `POST /items`, tracked).
- `totalPaid` required unless every line has `unitCost` (then total = Σ quantity × unitCost).
- Lines with `unitCost` keep it; the rest share `totalPaid − Σ given` (must be ≥ 0) by `quantity × price`
  (all prices 0 → by quantity).
- Per line: `RECEIVED +quantity` (note "Purchase from {supplier}" / "Purchase"), tracking on, `costPrice = unitCost`.
- One transaction.

## Read
`GET /purchases?status=OPEN|ALL&itemId=&page=0&size=20` (newest `boughtOn` first; `itemId` = only purchases with that item) and `GET /purchases/{purchaseId}` →
`Purchase { id, supplier, boughtOn, totalPaid, note, items[], itemCount, left%, costLeft, soldValue, paidBack, profit, finished, canDelete }`
- `items[]`: `{ itemId, name, unit, quantity, left, unitCost, price }`; `left` from FIFO, clamped 0..quantity.
- `costLeft = Σ left × unitCost`; `leftPercent = costLeft / totalPaid` (0..100).
- `soldValue = Σ (quantity − left) × price`; `paidBack = soldValue ≥ totalPaid`;
  `profit = soldValue − (totalPaid − costLeft)`.
- `finished` = every line left 0. `OPEN` = not finished.
- `canDelete` = every line left = quantity.

## FIFO (per item)
In time order, every positive movement (`OPENING`, `RECEIVED`, positive `COUNT`) is a bucket. Used = Σ |`SALE`,
`REMOVED`, negative `COUNT`| − Σ `SALE_CANCELLED` (≥ 0). Buckets fill oldest first. Movements tagged with a deleted
purchase's id are skipped. A purchase line's left = its bucket's unused part.

## Delete `DELETE /purchases/{purchaseId}`
Only when `canDelete`; writes `REMOVED −quantity` (tagged with the purchase) per line, marks the purchase inactive.

## Tests
Cost split (total only, mixed given/estimated, over-given rejected, zero prices); FIFO across opening stock and two
purchases, cancel restores, deleted purchase ignored; paidBack/profit; create with a new item; delete rules; other owner.
