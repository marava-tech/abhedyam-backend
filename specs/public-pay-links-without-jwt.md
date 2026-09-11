# Public pay links without a login token

## Problem
`GET /api/v1/public/payment-links/{token}` is permitAll, but the JWT filter
still ran when a Bearer token was forwarded (the web proxy always attaches
the session). A stale or missing-user token returned 401, so a copied pay
link failed in the same browser that created it. Created links also relied
on the Java field default for `isActive`, which Hibernate can omit.

## Solution
Skip JWT validation for `/api/v1/public/**`. Set `isActive=true` when
creating a payment link.

## API Contract
Unchanged. Public GET still returns the existing payload.

## Data Model
None.

## Edge Cases
No Authorization header: unchanged. Invalid Bearer on `/public/**`: now
served instead of 401.

## Out of Scope
Changing the pay-link URL shape or expiry.
