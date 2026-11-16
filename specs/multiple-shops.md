# Multiple shops (Business plan)

Plan: `abhedyam-owner/.product/BUSINESS_PLAN.md` step 5. Unlimited shops on Business; each shop has its own helpers, phones (10 each on Business), customers, reports and khata links.

## Model
- An extra shop is its own `owners` row with `parent_owner_id` = the main shop. It has no email / firebaseUid / phone, and `access_until` null. Every `/owners/{id}/...` call, helper, report and khata link works unchanged with the extra shop's id.
- The Gmail always signs in to the **main shop**. The plan, trial, founding seat and Play subscription live on the main shop: `OwnerRepository.main(o)` / `plan(o)`.
- `GET /billing/me` on an extra shop returns the main shop's access and plan, with `mainShop=false` (the app hides buying there). A RevenueCat event for an extra shop's id is applied to the main shop.

## API (owner only; members get 403 NOT_ALLOWED)
- `GET /api/v1/owners/{id}/shops` → main shop first, then extra shops oldest first: `{id, name, main, current, paused, toCollect, colour}`. `toCollect` = that shop's Home "To collect" (`OwnerService.toCollect`); `colour` = palette index 0-7 the owner picked, null = the app picks (main shop indigo, extra shops by id).
- `PATCH /api/v1/owners/{id}/shops/{shopId}` `{businessName?, colour?}` → rename a shop and/or pick its colour (`owners.shop_colour`). Any of the owner's shops (main, extra, paused); others 404. Returns the updated shop.
- `POST /api/v1/owners/{id}/shops` `{businessName, phone?}` → Business only (else 400 `PLAN_LIMIT`). Returns an `AuthResponse` for the new shop. Bill phone = `phone` (10 digits) or the main shop's; `phone_normalized` stays empty on extra shops (unique per account, they never sign in by phone; the owner update also leaves it alone for extra shops). The main shop's settings (setup answers in `otherFlags`, villages, location tools, Home tools) are copied, so the app opens the new shop on Home with no setup and no referral question (2026-11-15, app plan `.product/MULTI_SHOP_REDESIGN.md`).
- `POST /api/v1/owners/{id}/shops/{shopId}/switch` → `AuthResponse` for that shop. The target must be the main shop or one of its extra shops (else 404).
- Adding or switching ends this phone's sign-in to the shop it leaves, so Your devices lists each phone once.

## Pushes for every shop
- A phone registers its push token under the shop it is in. So an inbox push (`NotificationDispatcher`) goes to that shop's phones **and** (`FcmService.sendToOtherShops`) to the owner's own phones open in another of their shops: owner sessions only (`member_id` null), never helpers, never paused shops.
- Every inbox push carries `shopId`; the other-shop copy also has `shopName` and the title starts with it ("Test Shop 2 · Prava paid ₹1,000"). The app switches to `shopId` before opening the item.

## When Business ends
- Extra shops are **paused**, nothing is deleted: everyone signed in to them (owner phones, helpers) is logged out, and sign-in, refresh and switch return 400 `PLAN_PAUSED` until Business is bought again. Runs from `enforcePlan(main)` after every Play event.
- Back on Business: the shops open again; helpers sign in again.

## Delete and export
- Deleting the main shop disables its extra shops too (they can only be reached through its Gmail).
- An extra shop's data export and its profile email use the main shop's Gmail.
