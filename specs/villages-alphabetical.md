# Villages listed in alphabetical order

## Problem
`GET /api/v1/location-details/villages` (and the unpaged owner-village
helpers) ordered by `customerCount DESC`. Shopkeepers look a village up by
name; a popularity ranking makes Adilabad appear under a village with more
customers.

## Solution
All village-name listings now sort **A–Z** (`LOWER(TRIM(village))` on the
grouped queries; `village ASC` on `DISTINCT` queries so MySQL accepts the
`ORDER BY`). Customer-count is still returned; it is no longer the sort key.

`GET /api/v1/owners/{ownerId}/customers` already groups by village then name
when `sortBy=village` (the default). No change there.

## Changes
- `repository/LocationDetailsRepository.java` — 6 listing queries

## Compatibility
Response shape unchanged. Cached village keys expire on the existing TTL /
`invalidateOwnerCaches`.
