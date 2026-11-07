# Payment dues (multiple due dates per customer)

**Need:** a customer pays what they owe in several parts. The owner records each promise ("₹5,000 by 12 Nov") from the customer detail screen and sees which are paid, partly paid or missed.

## Model
`payment_dues`: `id, ownerId, customerId, amount, dueDate (LocalDate), note (≤200, optional)` + BaseEntity. Delete = soft delete (`isActive=false`).

Dues belong to the **customer**, not a sale: payments are made against the customer's balance, and the balance stays the source of truth. Nothing about sales, payments or balances changes.

## How a due gets paid (computed, never stored)
Successful payments are replayed in the order they were recorded (`createdAt`). Each payment fills the open due with the earliest `dueDate` that **already existed** when the payment was recorded. A payment recorded before any due existed counts for none. Money beyond all dues is left over (it still lowers the balance).
Because it is recomputed on every read, deleting, confirming or failing a payment updates dues by itself.

Status: `PAID` (paid = amount) · `MISSED` (date before today, owner's time zone, not fully paid) · `PARTLY_PAID` · `OPEN`.

## Rules
- amount > 0; dueDate required, not before today when created or moved.
- New/edited due: its unpaid part + the unpaid part of the other dues ≤ the customer's balance ("Only ₹X is left to plan").
- Editing can't lower a due below what is already paid against it.

## API (owner only, own customers)
`/api/v1/owners/{ownerId}/customers/{customerId}/dues`
- `GET` → `{ balance, planned, unplanned, missedCount, next, dues[] }` where `planned` = unpaid part of all dues, `unplanned = max(0, balance − planned)`, `next` = earliest not-paid due. Each due: `id, amount, paid, remaining, dueDate, note, status, createdAt`. Sorted by dueDate.
- `POST {amount, dueDate, note}` → the due
- `PUT /{dueId} {amount?, dueDate?, note?}` → the due
- `DELETE /{dueId}`

## Owner-wide, reminders, digest (slices 3-5)
- `GET /api/v1/owners/{ownerId}/dues?days=7` → `{ upcomingAmount, missedCount, missedAmount, dues[] }`; each item `{customerId, customerName, customerPhone, due}`; missed first, then unpaid dues up to `days` ahead (max 90). For Home "Due this week".
- `remindOwner` on a due (create/edit). At the owner's digest time the day before, an `OWNER_REMINDER` push "Prava pays tomorrow · ₹5,000 due on Thu, 5 Nov", opening that customer (`relatedEntityType=CUSTOMER`). Dedupe key `due-remind:{id}:{date}`, so moving the date re-arms it.
- Daily digest starts with "2 dues today ₹7,000 · 1 missed ₹3,000" when there are any.
- Messaging the customer is app-only: the phone's SMS/WhatsApp composer with a ready text; nothing is sent by the server.

## Later slices
App Dues card + sheet (slice 2), message the customer from a due (3), owner push + digest by dues (4), Home "Due this week" (5). Optional sale link and a split helper in New sale only if asked.
