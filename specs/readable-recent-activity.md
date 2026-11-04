# Readable recent activity (short headline, structured detail, customer link)

## Problem
Recent-activity cards show machine-style sentences ("Created a new sale
transaction for customer X with total amount ₹16000.0. Transaction ID: <uuid>")
and the rows carry no structured data, so the app cannot show who/what/how or
link to the customer. Owners find the expanded text boring and hard to scan.

## Solution (additive)
New activity rows are written with short text and three extra nullable fields,
returned by `GET /api/v1/stats/recent-activities`:
- `customerId`, `customerName` — who it was about (sale, payment, reminder)
- `detail` — one short plain line shown when the card is expanded
  - sale: `Rice x2, Oil · Paid ₹500, due ₹1,900` (or `On udhar`, or `Paid in full`)
  - payment: `Cash` / `UPI` + ` · for Rice`
  - reminder: `Due 12 Nov, 5:00 PM`
- shorter headlines for NEW rows (the amount is already a separate field):
  `Sale to Prava`, `Payment from Prakash T`, `Reminder set for Prakash T`
- amounts inside any text use Indian grouping without trailing zeros: `₹16,000`,
  `₹1,234.50` (not `₹16000.0`).
Old rows keep their old text and have the new fields null; clients must fall back.

## API Contract
`RecentActivityResponse` gains `customerId` (uuid|null), `customerName`
(string|null), `detail` (string|null). No request changes. No behaviour change
for existing fields.

## Data Model
`audits`: add nullable `customer_id` VARCHAR(36), `customer_name` VARCHAR(255),
`detail` VARCHAR(255) (Hibernate `ddl-auto: update` adds them).

## Edge Cases
- Detail longer than 255 chars is truncated with an ellipsis.
- More than 3 sale items: first 3 then `+N more`.
- Quantity 1 omits the `x1`; fractional quantities print without trailing zeros.
- Audit logging must never fail the business action (existing try/catch kept).

## Out of Scope
Backfilling old rows, localisation (Telugu/Hindi text), changing which events
appear in the feed, cancel/undo from the feed.
