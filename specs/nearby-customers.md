# Nearby customers (list)

Product plan: abhedyam-android `.product/NEARBY_PLAN.md`. Owner decisions (2026-11-18): one list replaces the single "nearest customer" card and the separate Route on map tool. Only people who owe are listed.

## Behaviour
- `GET /api/v1/owners/{ownerId}/nearby?lat=&lng=&limit=` (owner token, own shop only). `limit` defaults to 20, range 1–50.
- Lists the shop's customers who:
  - **owe money** (sales minus successful payments, the same sums as the village totals)
  - are **active**
  - are **not** marked "not getting back" (`chasePaused`)
  - have a **saved location**

  Closest first, by straight-line distance from `lat,lng`.
- **No village-name filter.** The old `/customers/nearest` kept only customers whose typed village matched the phone's town name, so a spelling difference hid everyone.
- Each row carries what the app needs for the money line, from the same dues as Upcoming collections (real dues and collection-cycle days):
  - `overdueDays`: days since the oldest overdue due, else null.
  - `nextDueDate`: the earliest unpaid due that isn't overdue (today counts), else null.
- `owingWithoutLocation`: how many customers who owe (same filters) have no saved location, for the "N have no location yet" row.
- No customers at all is an empty list, not an error.

## Response
`NearbyCustomersResponse { customers: NearbyCustomer[], owingWithoutLocation: long }`

`NearbyCustomer { customerId, name, phone, village, balance, distanceMeters, latitude, longitude, overdueDays?, nextDueDate? }`

## Errors
- `lat` / `lng` missing or out of range: 400 `VALIDATION_ERROR`.
- Another owner's id: 403, the same ownership check as `/dues`.

## Later
`POST /customers/nearest` goes once the app no longer calls it (there are no users yet, so nothing to keep compatible).
