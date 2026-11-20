# Reports for a date range

Owner app Reports redesign, phase 1 (Claude doc "Abhedyam Owner App Audit", 2026-11-20). Reports showed sales only;
a khata shop's question is "is my outside money growing?".

## Endpoint
`GET /api/v1/stats/report?from=YYYY-MM-DD&to=YYYY-MM-DD` — the signed-in shop (owner id from the token, never a
parameter), both days whole, in the shop's time zone (`notification_settings.timezone`). At most 366 days.

```json
{ "from": "2026-11-01", "to": "2026-11-20",
  "sold": 68850, "bills": 7, "customers": 6,
  "collected": 69050, "collectedCash": 51000, "collectedUpi": 18050, "writtenOff": 0,
  "days": [ { "date": "2026-11-01", "sold": 0, "bills": 0, "collected": 1500 }, ... ] }
```

- **sold / bills / customers**: active sale lines (`is_active`) by `created_at` (backdated for offline sales).
- **collected**: SUCCESS payments by `timestamp` (when received), write-offs left out. Cash = CASH + OFFLINE; UPI =
  UPI, PHONEPE, PAYTM, IN_APP.
- **writtenOff**: write-off payments: lowers what is owed without money coming in.
- **days**: every day in the range, empty days included (the app folds them).

The app derives **change in To collect = sold − collected − writtenOff** and compares each number with the previous
period (it asks twice). "Given on udhar" was dropped: payments are linked to sale lines later too (PaymentAllocator),
so "paid at the counter" can't be told apart from "paid later".

## Errors
`INVALID_RANGE` (to before from), `RANGE_TOO_LONG` (> 366 days).

## Not in phase 1
Top items / customers / villages, profit, daybook, share as PDF.
