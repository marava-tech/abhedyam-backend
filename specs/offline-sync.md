# Offline entries (payments, sales) — server side

Full plan and research: `abhedyam-owner/.product/OFFLINE_SYNC_PLAN.md`.

The phone saves a Payment in / New sale it could not send (no network, timeout, 5xx) and sends it later, possibly
hours later and possibly more than once (a timeout may hide a request the server did get). The server must:

1. **Record each client key once, forever.** `idempotencyKey` is stored on every row it made (`payments.client_key`,
   `sale_items.client_key`, index `owner_id, client_key`). A repeat returns what was recorded the first time
   (`CollectPaymentResponse` with the first rows; the first sale's `SaleDetailResponse`), never a second record.
   Redis stays only for two copies arriving at the same moment.
2. **Date entries when they happened.** `paidAt` (payments, existing) and `soldAt` (sales, new): not in the future
   (5 min slack), at most a year back, default now. A sale with `soldAt` gets `sale_items.created_at = soldAt`, and its
   paid-at-sale payments `timestamp = soldAt`. Recent activity and stock history keep the time it reached the server.
3. A real "no" (4xx) is final for that entry: the phone keeps it for the owner to look at.
