# Bring in old records: add many customers with what they owe (2026-11-18)

App plan: `abhedyam-android/.product/IMPORT_PLAN.md` (slice 1). One request saves a list from the phone's contacts or a typed list.

## API

`POST /api/v1/owners/{ownerId}/customers/batch?dryRun=false`

```json
{ "idempotencyKey": "uuid from the app, same on a retry",
  "label": "Old balance",            // the sale's line name, in the owner's language; default "Old balance"
  "rows": [ { "name": "Ramesh", "phone": "98480 12345", "village": "Kondapur", "owes": 4000, "dueDate": "2026-12-01" } ] }
```

Answer: `{ saved, newCustomers, withAmount, totalOwes, problems: [ { row, code } ], rows: [ { row, name, owes, status } ] }`. `row` counts from 1. `status` is `ADDED`, `EXISTING` or a problem code; `newCustomers`/`withAmount`/`totalOwes` count `ADDED` rows only, `saved` = `ADDED` + `EXISTING`.

## Rules

- Only the owner themself (the same check as file import). 1 to 500 rows. `idempotencyKey` is required, up to 40 characters.
- Each row: find the customer by phone, or by name (ignoring case and extra spaces) when there is no phone. Otherwise create them with name, phone and village.
- **Already in the shop (2026-11-18):** the row is `EXISTING`: nothing is created and **their old balance is not added**. Their balance in the shop is the truth; adding it again (a second or edited file, contacts already there) would double what they owe. Exception: when a sale with this list's key (`idempotencyKey:row`) exists, it is a retry of this same list, so the row stays `ADDED` and the sale is found, not repeated.
- `owes` > 0 adds one *just an amount* sale named `label`, dated now, with the optional `dueDate`. Its client key is `idempotencyKey:row`, so a retry finds the same sale instead of adding it twice. A retry then reports the customers as found, not new: `saved` and `totalOwes` stay right, and `totalOwes` stays right.
- The same phone or name twice in one list: the later row is a `DUPLICATE` and is skipped.
- Problem codes: `NO_NAME`, `BAD_PHONE` (not 10 digits), `PHONE_TAKEN` (the phone is another shop's customer), `DUPLICATE`, `BAD_AMOUNT` (negative), `BAD_DATE` (not yyyy-MM-dd), `FAILED` (anything else, logged). A bad row is skipped and the others still save.
- `dryRun=true` checks the rows and counts, and saves nothing.

## From an Excel file (slice 4, 2026-11-18)

`POST /api/v1/owners/{ownerId}/bulk-import?dryRun=false&label=Old%20balance` (multipart `file`, .xlsx up to 10 MB, 2000 rows). Answer: the same `CustomerBatchResult` as above, with `row` = the row number Excel shows.

- Columns: Name, Phone, Village, Owes, Due date. The header row (one of the first 5 rows) can be in English, Telugu or Hindi and in any order; without one, the first sheet is read in that order from row 1.
- Phone as a number cell or text; Owes as a number or text like `₹4,000` / `Rs. 4000` (other text: `BAD_AMOUNT`); Due date as a date cell, `dd/mm/yyyy` or `yyyy-mm-dd` (other text: `BAD_DATE`).
- Every row then goes through the batch code above. The idempotency key is the file's SHA-256, so the same file again finds its customers and old-balance sales instead of adding them twice.
- `GET /api/v1/bulk-import/template?lang=en|te|hi`: the empty 5-column file, made in code.
- The old 8-column template (product, price, paid, paid date, deadline) is gone (owner decision 3a, no users yet).
