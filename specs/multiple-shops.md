# Multiple shops (Business plan)

Plan: `abhedyam-owner/.product/BUSINESS_PLAN.md` step 5. Unlimited shops on Business; each shop has its own helpers, phones (10 each on Business), customers, reports and khata links.

## Model
- An extra shop is its own `owners` row with `parent_owner_id` = the main shop. It has no email / firebaseUid / phone, and `access_until` null. Every `/owners/{id}/...` call, helper, report and khata link works unchanged with the extra shop's id.
- The Gmail always signs in to the **main shop**. The plan, trial, founding seat and Play subscription live on the main shop: `OwnerRepository.main(o)` / `plan(o)`.
- `GET /billing/me` on an extra shop returns the main shop's access and plan, with `mainShop=false` (the app hides buying there). A RevenueCat event for an extra shop's id is applied to the main shop.

## API (owner only; members get 403 NOT_ALLOWED)
- `GET /api/v1/owners/{id}/shops` → main shop first, then extra shops oldest first: `{id, name, main, current, paused}`.
- `POST /api/v1/owners/{id}/shops` `{businessName}` → Business only (else 400 `PLAN_LIMIT`). Creates the shop and returns an `AuthResponse` for it with `isNewUser=true` (the app runs shop setup).
- `POST /api/v1/owners/{id}/shops/{shopId}/switch` → `AuthResponse` for that shop. The target must be the main shop or one of its extra shops (else 404).
- Adding or switching ends this phone's sign-in to the shop it leaves, so Your devices lists each phone once.

## When Business ends
- Extra shops are **paused**, nothing is deleted: everyone signed in to them (owner phones, helpers) is logged out, and sign-in, refresh and switch return 400 `PLAN_PAUSED` until Business is bought again. Runs from `enforcePlan(main)` after every Play event.
- Back on Business: the shops open again; helpers sign in again.

## Delete and export
- Deleting the main shop disables its extra shops too (they can only be reached through its Gmail).
- An extra shop's data export and its profile email use the main shop's Gmail.
