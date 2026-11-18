# Bring in old records: add many customers with what they owe (2026-11-18)

App plan: `abhedyam-android/.product/IMPORT_PLAN.md` (slice 1). One request saves a list from the phone's contacts or a typed list.

## API

`POST /api/v1/owners/{ownerId}/customers/batch?dryRun=false`

```json
{ "idempotencyKey": "uuid from the app, same on a retry",
  "label": "Old balance",            // the sale's line name, in the owner's language; default "Old balance"
  "rows": [ { "name": "Ramesh", "phone": "98480 12345", "village": "Kondapur", "owes": 4000, "dueDate": "2026-12-01" } ] }
```

Answer: `{ saved, newCustomers, withAmount, totalOwes, problems: [ { row, code } ] }`. `row` counts from 1.

## Rules

- Only the owner themself (the same check as file import). 1 to 500 rows. `idempotencyKey` is required, up to 40 characters.
- Each row: find the customer by phone, or by name (ignoring case) when there is no phone. Otherwise create them with name, phone and village.
- `owes` > 0 adds one *just an amount* sale named `label`, dated now, with the optional `dueDate`. Its client key is `idempotencyKey:row`, so a retry finds the same sale instead of adding it twice. A retry then reports the customers as found, not new: `saved` and `totalOwes` stay right, but `newCustomers` can be lower.
- The same phone or name twice in one list: the later row is a `DUPLICATE` and is skipped.
- Problem codes: `NO_NAME`, `BAD_PHONE` (not 10 digits), `PHONE_TAKEN` (the phone is another shop's customer), `DUPLICATE`, `BAD_AMOUNT` (negative), `BAD_DATE` (not yyyy-MM-dd), `FAILED` (anything else, logged). A bad row is skipped and the others still save.
- `dryRun=true` checks the rows and counts, and saves nothing.

## Not in this slice

The Excel file path keeps its 8 columns until the app's Excel step (slice 4) moves it to Name, Phone, Village, Owes, Due date over this same code.
