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

## Village vs location split (2026-11-07, approved by owner)
Two independent features, each its own switch in owner settings:
- **Villages view** (`villageModeEnabled`, null = off): a village is a *label* for grouping customers and planning
  village visits. Never needs GPS.
- **Location tools** (`locationToolsEnabled`, null = on): a customer's *map pin*: save location, map, directions,
  nearby customers, visit route. Never edits the village on its own.

Storage stays in `location_details` (village column + optional pin) instead of moving village to `customers`:
same behaviour, ~10 lines instead of ~300 references across native queries.
- latitude/longitude are now nullable; a row can hold only a village. `VillagePinMigration` (startup, idempotent)
  relaxes NOT NULL, turns the 27 fake `0,0` pins into NULL and clears the "No Village" placeholder text.
- Customer create / New sale store a village only when one is typed; never `0,0`, never "No Village".
- `PUT .../location` accepts a pin, a village, or both; a pin never overwrites the village unless `village` is sent.
- Wire contract unchanged: a missing pin is still sent as `0` (released apps parse latitude as a required number).
- Data export lists every customer's village, pin or not.
