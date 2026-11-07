# Village mode (per owner) — spec

Status: approved by owner 2026-11-07 (backend per owner; hide villages everywhere when off).

## Problem
"Villages view" was a phone-only preference (SharedPreferences, default off). The backend could not know it was
off, so it still ordered every customer list by village (the app showed village headers under "All"),
returned village visits in Upcoming collections, and pushed "Visit Hanamkonda tomorrow".

## Change
- `owner_settings.village_mode_enabled` (nullable BIT; null = off). Read/write via the existing
  `GET/PATCH /api/v1/owners/{ownerId}/settings` as `villageModeEnabled`.
- When off:
  - `GET /owners/{id}/customers` with no `sortBy` orders by name (not village) and search matches name/phone only.
  - `GET /owners/{id}/dues` (Upcoming collections) returns `visits: []`.
  - `DueDigestJob` sends no village visit reminders.
- When on: unchanged behaviour.
- Village data is never deleted; turning the mode on brings everything back.

## App (separate PR)
Reads the flag from settings, migrates the old phone preference once (phone said on, server unset → PATCH true),
and hides village UI everywhere when off (list headers, search hint, forms, rows, detail, bills, visits, Round).

## Tests
DueDigestJob: visit reminded when on, not reminded when off. CustomerService default sort follows the flag.
