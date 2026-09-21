# garage module

Manages each user's garage (one per user) and the cars and modifications inside it.
Owns the `garages`, `cars`, `car_modifications`, `car_share_links`, `dream_cars` and the
seven car-reference lookup tables (`car_brands`, `car_models`, `car_drivetrain_options`,
`car_color_options`, `car_distance_units`, `car_status_options`, `car_mod_categories`).

> `dream_cars` (a user's wishlist) lives here rather than in `profile` because it
> references `car_brands`/`car_models`. The owning user is stored as a raw
> `profile_id` UUID, mirroring `garages.owner_id`.

Depends on the `profile` module for username resolution and privacy flags, and on
the `follow` module for the accepted-follower check used to gate access to private
users' garages.

## Public API

### Service interface — `GarageService`

| Method | Description |
|---|---|
| `findCarsByIds(ids)` | Batch compact car summaries by id, no privacy gating (cross-module, e.g. post car tags) |
| `findCarOwnerIds(carIds)` | Map of car id → owner id, no privacy gating (lets posts enforce that a tagged car's owner is tagged) |
| `findCarIdsByOwner(ownerId)` | The inverse: every car id one profile owns (lets `tags` find the content a user's cars are tagged in, and lets untagging drop the caller's own car tags) |
| `findModShareCards(modificationIds)` | Batch feed cards for shared build-log mods, derived on read, never stored (lets `posts` draw a mod card without reaching into the garage's tables) |
| `findOwnedModificationCarId(ownerId, modificationId)` | The car a mod sits on, only if that user owns it — the gate `posts` shares a mod through, plus the car id it needs to tag |
| `getMyGarage(currentUserId)` | The caller's own garage with car summaries |
| `getGarageByUsername(currentUserId, username)` | Another user's garage; private profiles gated by follow status |
| `addCar(currentUserId, CreateCarRequest)` | Creates car + mods in one transaction and returns presigned upload URLs for all photos |
| `updateCar(currentUserId, carId, CarRequest)` | Full replacement of an owned car |
| `deleteCar(currentUserId, carId)` | Deletes an owned car; mods and gallery images cascade |
| `getCar(currentUserId, carId)` | Car detail including modifications; privacy-gated |
| `addModification(currentUserId, carId, CarModificationRequest)` | Adds a mod to an owned car and returns presigned before/after upload URLs |
| `updateModification(currentUserId, carId, modificationId, CarModificationRequest)` | Full replacement of an owned mod |
| `deleteModification(currentUserId, carId, modificationId)` | Deletes an owned mod |
| `refreshCoverUploadUrl(currentUserId, carId)` | Refreshes the presigned upload URL for a car's cover image |
| `refreshModificationUploadUrls(currentUserId, carId, modificationId)` | Refreshes presigned upload URLs for a modification's before/after images |
| `listCarImages(currentUserId, carId)` | Gallery images for a car ordered by display order; privacy-gated |
| `deleteCarImage(currentUserId, carId, imageId)` | Deletes a gallery image record; only the car owner may delete |
| `generateCarImageDownloadUrl(currentUserId, storagePath)` | Returns a short-lived presigned download URL after verifying view permission |
| `listBrands()` / `listModelsByBrand(brandId)` / `listDrivetrains()` / `listColors()` / `listDistanceUnits()` / `listStatusOptions()` / `listModCategories()` | Reference data for client dropdowns |
| `listDreamCars(currentUserId)` | The caller's dream cars in display order |
| `addDreamCar(currentUserId, DreamCarRequest)` | Appends one or more dream cars to the caller's list; returns the created cars |
| `updateDreamCar(currentUserId, dreamCarId, DreamCarRequestBody)` | Updates an owned dream car (brand/model) |
| `deleteDreamCar(currentUserId, dreamCarId)` | Removes an owned dream car |

### DTOs / records

| Type | Used for |
|---|---|
| `GarageDto` | Garage view with embedded list of `CarSummaryDto` |
| `CarSummaryDto` | Compact list item (brand, model, year, horsepower, torque, cover image) |
| `CarDto` | Full car detail with embedded `CarModificationDto` list |
| `CarModificationDto` | A single modification, incl. `sharedPostId` — the feed post it was shared as, or null |
| `CreateCarRequest` | Single-shot "add car" payload: car specs + modifications + `galleryCount` (0–50); no file bytes |
| `CreateCarResponse` | Result of car creation: `CarDto` + `cover` upload slot + per-modification before/after slots + gallery slots |
| `UploadSlot` | A presigned upload destination: `path` + `uploadUrl` |
| `ModificationUploadSlots` | Before/after `UploadSlot` pair for one modification, keyed by `modificationId` |
| `GallerySlot` | `imageId` + `path` + `uploadUrl` for one pre-allocated gallery image |
| `CarImageDto` | A gallery image record: `id`, `storagePath`, `displayOrder`, `createdAt` |
| `CarImageDownloadRequest` | Request body for the download-URL endpoint: `storagePath` |
| `SignedUrlResponse` | Wraps a single short-lived `signedUrl` |
| `CarRequest` | Create / replace payload for a car (validated) |
| `CarModificationRequest` | Create / replace payload for a modification (validated; enforces `isPricePublic ⇒ price != null` via `@AssertTrue`) |
| `ModShareCardDto` | A modification as the feed draws it when a post shares one — a hand-written projection, derived on read. No R2 keys beyond the media's, and no price unless the owner published it |
| `CarBrandDto`, `CarModelDto`, `CarDrivetrainDto`, `CarColorDto`, `CarDistanceUnitDto`, `CarStatusOptionDto`, `CarModCategoryDto` | Reference data |
| `DreamCarDto` | A dream car with denormalized brand/model names, createdAt |
| `DreamCarRequest` | Create payload: a non-empty `dreamCars` list of `DreamCarRequestBody` |
| `DreamCarRequestBody` | A single dream car: `brandId` (required), `modelId` (optional). Also the body of a single-item update |
| `CarShareDto` | The owner's view of a share link: `code`, `url`, `qrUrl`, `enabled`, `createdAt`, `viewCount`, `qrScanCount`, `lastViewedAt` |
| `CarShareQrDto` | `code` + the rendered SVG bytes, returned together so the controller can name the download without a second lookup |
| `CarShareResolutionDto` | `carId` + `ownerUsername` — all the app needs to open its own car screen |
| `ShareLinkUpdateRequest` | `{ "enabled": bool }`. The only editable field; there is no regenerate |
| `ShareSource` | `LINK` / `QR`, parsed from the URL's `?s=` tag |
| `PublicCarDto`, `PublicCarModificationDto`, `PublicCarOwnerDto`, `PublicBadgeDto`, `PublicMediaDto`, `PublicCarEventDto`, `PublicCarPlacementDto` | The public page's shape — a hand-written projection, never a rename of `CarDto`. No ids, no R2 keys, no licence plate |
| `PublicCarEventsProvider` | The events a car attended, for the public page. Implemented by Map Events (`PublicCarEventsAdapter`), because garage can't depend on that module. The same pattern as `storage.UploadAccessPolicy` |
| `ModSharePostsProvider` | Which of a car's mods are already in the feed, and as which post. Implemented by posts (`ModSharePostsAdapter`), because posts already depends on garage. Same inversion, same reason — it is what lets the build log offer to share an unshared mod and link to the post for a shared one |

### Share links (public link + QR code)

| Method | Description |
|---|---|
| `ensureShareLink(currentUserId, carId)` | The car's share link, minting one on first call. **Idempotent** — the app POSTs on every share-sheet open and must always get the same code back |
| `getShareLink(currentUserId, carId)` | The car's share link; 404 if never shared |
| `setShareLinkEnabled(currentUserId, carId, enabled)` | Pause / resume. The code never changes, so a printed sticker survives both |
| `renderShareQrSvg(currentUserId, carId)` | The share URL as a print-ready SVG QR code, plus the code it encodes |
| `revokeShareLinksForCar(carId)` | Retires the live link so its code answers 410 forever. **No HTTP caller** — see *Transfers* below |
| `resolveShareCode(currentUserId, rawCode, source)` | Code → `{carId, ownerUsername}` for the installed app. Authenticated |
| `getPublicCar(rawCode, source, countView)` | The public page. **Unauthenticated** |

### Exceptions

| Exception | HTTP | Trigger |
|---|---|---|
| `GarageNotFoundException` | 404 | A user's garage row is missing (should not happen if the `on_profile_created_create_garage` trigger is active) |
| `CarNotFoundException` | 404 | Car id doesn't exist |
| `CarModificationNotFoundException` | 404 | Modification id doesn't exist (or doesn't belong to the path's car id) |
| `CarImageNotFoundException` | 404 | Gallery image id doesn't exist or doesn't belong to the path's car id |
| `NotCarOwnerException` | 403 | Caller is not the owner of the car they're trying to mutate |
| `DreamCarNotFoundException` | 404 | Dream car id doesn't exist or isn't owned by the caller (queries are owner-scoped) |
| `InvalidReferenceException` | 400 | Request references an unknown brand / model / drivetrain / color / unit / category, or a model that doesn't belong to the brand id provided |
| `ShareLinkNotFoundException` | 404 | Share code unknown or malformed, or the owner asked for a link on a car they have never shared |
| `ShareLinkGoneException` | 410 | The link is paused, revoked (transfer), or its owner is banned. Distinct from 404 for crawlers only — the website renders the same screen for both |
| `InvalidStoragePathException` | 400 | `storagePath` in a download-url request doesn't match the canonical `car-photos/{ownerId}/{carId}/...` format |

## REST endpoints

Base path: `/api/v1/garage`

### Garage views

| Method | Path | Description |
|---|---|---|
| GET | `/me` | Your own garage |
| GET | `/by-username/{username}` | Another user's garage (privacy-gated) |

### Cars

| Method | Path | Description |
|---|---|---|
| POST | `/cars` | Single-shot car creation: persists car + mods, returns presigned upload URLs (201) |
| PUT | `/cars/{carId}` | Replace your car |
| DELETE | `/cars/{carId}` | Delete your car (204) |
| GET | `/cars/{carId}` | Get car detail with modifications (privacy-gated) |
| POST | `/cars/{carId}/cover-upload-url` | Get a fresh presigned upload URL for the cover image (e.g., when URL expires) |

### Modifications

| Method | Path | Description |
|---|---|---|
| POST | `/cars/{carId}/modifications` | Add a mod to your car (201) |
| PUT | `/cars/{carId}/modifications/{modificationId}` | Replace one of your mods |
| DELETE | `/cars/{carId}/modifications/{modificationId}` | Delete one of your mods (204) |
| POST | `/cars/{carId}/modifications/{modificationId}/upload-urls` | Get fresh presigned URLs for before/after images (e.g., when URLs expire) |

### Gallery images

| Method | Path | Description |
|---|---|---|
| GET | `/cars/{carId}/images` | List gallery images ordered by display order (privacy-gated) |
| DELETE | `/cars/{carId}/images/{imageId}` | Delete a gallery image record (204; owner only) |
| POST | `/storage/download-url` | Exchange a `storagePath` for a short-lived presigned download URL |

> Upload URLs for cover, gallery and modification media come from the storage module
> (`/api/storage/cars/{carId}/...`), car owner only (`CarUploadAccessPolicy`). Clients PUT bytes
> straight to R2, then attach the keys here. Attach endpoints reject keys outside
> `cars/{carId}/cover/`, `cars/{carId}/gallery/` or `cars/{carId}/modifications/{modId}/` that are
> not already stored on the car, and replacing a cover deletes the previous object after commit.

### Dream cars

| Method | Path | Description |
|---|---|---|
| GET | `/dream-cars` | Your dream cars (oldest first) |
| POST | `/dream-cars` | Add one or more dream cars from a `dreamCars` list (201) |
| PUT | `/dream-cars/{dreamCarId}` | Update one of your dream cars (single `DreamCarRequestBody`) |
| DELETE | `/dream-cars/{dreamCarId}` | Remove one of your dream cars (204) |

### Share links

Owner-only. Base path `/api/v1/garage/cars/{carId}/share`.

| Method | Path | Description |
|---|---|---|
| POST | `` | The car's share link, creating one on first call. Always 200 — it is idempotent |
| GET | `` | The car's share link; 404 if never shared |
| PATCH | `` | `{ "enabled": bool }` — pause / resume |
| GET | `/qr.svg` | `image/svg+xml`, `Content-Disposition: attachment; filename="tweakd-{code}.svg"`, `Cache-Control: private, no-store` |

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/garage/share/resolve/{code}?s=` | **Authenticated.** Code → `{car_id, owner_username}` for a deep link the installed app received |

### Public car page (unauthenticated)

| Method | Path | Description |
|---|---|---|
| GET | `/public/v1/cars/{code}?s=` | The public build sheet behind `https://web.tweakdapp.com/c/{code}`. `Cache-Control: public, max-age=60, s-maxage=300`. 404 unknown / malformed, 410 paused / revoked / owner banned |

> **Not** under `/api/v1`. `/public/**` is `permitAll` in `SecurityConfig`, and this is
> the first and so far only route under it. Anything added there is on the open internet
> and must return a hand-written projection, never a DTO the app happens to share.
>
> The website's Cloudflare Pages Function fetches this at the edge and injects the `og:*`
> tags into `index.html`; browsers never call it directly, which is why the backend needs
> no CORS configuration.

### Reference data (for dropdowns)

| Method | Path |
|---|---|
| GET | `/reference/brands` |
| GET | `/reference/brands/{brandId}/models` |
| GET | `/reference/drivetrains` |
| GET | `/reference/colors` |
| GET | `/reference/distance-units` |
| GET | `/reference/status-options` |
| GET | `/reference/mod-categories` |

## Entities

### `GarageEntity` → `garages`

| Column | Type | Notes |
|---|---|---|
| id | UUID | PK; app-generated on insert via `UUID.randomUUID()` |
| ownerId | UUID | UNIQUE, FK → `profiles.id` |
| createdAt | Instant | DB default `now()`; `insertable=false, updatable=false` |

### `CarEntity` → `cars`

`@ManyToOne(LAZY)` to `GarageEntity`, `CarBrandEntity`, `CarModelEntity`,
`CarDrivetrainEntity`, `CarColorEntity`, `CarDistanceUnitEntity`. Scalars: year,
horsepower, torque, weight, engineDisplacement, optional `zeroToOneHundred`,
`chassisCode`, `engineCode`, `coverImageKey` (R2 key in the `cover_image_url` column).
`createdAt` is DB-managed.

Schema-derived id (`gen_random_uuid()` default in Supabase) is set by the application
with `UUID.randomUUID()`. `@DynamicUpdate` keeps PATCH-style updates lean.

### `CarModificationEntity` → `car_modifications`

`@ManyToOne(LAZY)` to `CarEntity` and `CarModCategoryEntity`. Scalars match the DB
schema. Application enforces `isPricePublic ⇒ price != null` at validation time;
the DB enforces the same via `car_modifications_price_visibility_check`.

### `CarImageEntity` → `car_images`

| Column | Type | Notes |
|---|---|---|
| id | UUID | PK; app-generated |
| carId | UUID | FK → `cars.id` |
| userId | UUID | FK → `profiles.id` (denormalized for fast ownership checks) |
| storagePath | String | UNIQUE; canonical `car-photos/{ownerId}/{carId}/gallery/{imageId}` path |
| displayOrder | int | Ascending sort position within the car's gallery |
| createdAt | Instant | DB default `now()`; `insertable=false, updatable=false` |

Cover image and modification before/after image paths are stored as plain `String` columns
on `CarEntity` and `CarModificationEntity` respectively — they are not rows in `car_images`.

### `CarShareLinkEntity` → `car_share_links`

| Column | Type | Notes |
|---|---|---|
| id | UUID | PK; app-generated |
| car | CarEntity | `@ManyToOne(LAZY)`, required (`car_id`), `ON DELETE CASCADE` |
| ownerId | UUID | FK → `profiles.id`, raw UUID (no cross-module entity ref), `ON DELETE CASCADE` |
| code | String | The public identifier. 10 Crockford-base32 chars, canonical uppercase, `updatable = false` |
| enabled | boolean | Owner's pause switch (`is_enabled`) |
| createdAt | Instant | DB default `now()`; read-only |
| revokedAt | Instant | Null = live. Set only by `revokeShareLinksForCar` |
| viewCount / qrScanCount / lastViewedAt | long / long / Instant | `insertable = false, updatable = false` — moved **only** by `CarShareLinkRepository.recordView`, a bulk update, so concurrent scans do not lose counts |

The code recipe and the reason for it live in `internal/share/ShareCodeGenerator`; the QR
in `internal/share/QrSvgRenderer` (ZXing `core` only — `javase` needs AWT, which the Cloud
Run image's slim JRE does not ship). `sharing.public-base-url` (default
`https://web.tweakdapp.com/c`, env `SHARING_PUBLIC_BASE_URL`) is where codes become URLs; the
database stores bare codes so a domain change is config, not a data migration.

### `DreamCarEntity` → `dream_cars`

| Column | Type | Notes |
|---|---|---|
| id | UUID | PK; app-generated on insert via `UUID.randomUUID()` |
| profileId | UUID | FK → `profiles.id` (owning user, raw UUID — no cross-module entity ref) |
| brand | CarBrandEntity | `@ManyToOne(LAZY)`, required (`brand_id`) |
| model | CarModelEntity | `@ManyToOne(LAZY)`, optional (`model_id`); must belong to the brand |
| createdAt | Instant | DB default `now()`; `insertable=false, updatable=false` |

All dream-car queries are scoped by `profileId`, so a user can only ever read or
mutate their own. `@DynamicUpdate` keeps updates lean.

### Reference entities

`CarBrandEntity`, `CarModelEntity` (with LAZY `@ManyToOne` to brand),
`CarDrivetrainEntity`, `CarColorEntity`, `CarDistanceUnitEntity`,
`CarStatusOptionEntity`, `CarModCategoryEntity` — read-only lookup rows seeded
in Supabase.

## Supabase triggers / constraints

| Trigger / Constraint | Source | Effect |
|---|---|---|
| `on_profile_created_create_garage` | AFTER INSERT on `profiles` | Inserts the user's empty garage row, so the Java layer never has to create one |
| `cars_garage_id_fkey ON DELETE CASCADE` | FK on `cars` | Deleting a garage drops its cars |
| `car_modifications_car_id_fkey ON DELETE CASCADE` | FK on `car_modifications` | Deleting a car drops its mods — `deleteCar` relies on this; the application does not explicitly clear them |
| `car_images_car_id_fkey ON DELETE CASCADE` | FK on `car_images` | Deleting a car drops its gallery rows |
| `car_modifications_price_visibility_check` | CHECK | `is_price_public = true` requires `price IS NOT NULL` |
| `posts_mod_share_modification_fkey ON DELETE SET NULL` | FK on `posts` | Deleting a mod clears the reference on any post that shared it — the post degrades to a plain one instead of breaking the feed |
| `posts_mod_share_modification_uq` | partial UNIQUE index | One post per modification. This, not the service's pre-check, is what makes `shareModification` idempotent under two simultaneous saves |
| `car_share_links_code_uq` | UNIQUE index | A share code is never reused — **including across revoked rows**, so a retired code can never come back pointing at a different car than the sticker it is printed on |
| `car_share_links_active_car_uq` | partial UNIQUE index `WHERE revoked_at IS NULL` | At most one live link per car. This, not the service's pre-check, is what makes `ensureShareLink` idempotent under two simultaneous taps of "Share" |
| `car_share_links_code_format_check` | CHECK | Only canonical Crockford base32 can be stored — the database half of the normalise-on-lookup contract |
| `car_share_links_car_id_fkey ON DELETE CASCADE` | FK | Deleting a car drops its share links, so a deleted car's code 404s instead of dangling |
| `car_share_links_owner_id_fkey ON DELETE CASCADE` | FK | Deleting an account drops its share links |

## Visibility

All accounts are public, so any garage and any car is viewable by any authenticated user;
there is no privacy gate.

**Sharing splits that in two.** The *share surface* — whether a car is shared, its code, its
QR and its counters — is **owner-only**: a non-owner gets 403, and cannot discover through
the API that a car is shared at all. The *public read* is unauthenticated and open to
anyone holding the code, but it is served through a hand-written projection
(`PublicCarDto`) rather than the in-app `CarDto`. That distinction is the safety property:
a field added to `CarDto` next year reaches the open internet only if somebody deliberately
adds it to `toPublicCarDto` too. Never public, whatever the car grows: the licence plate,
the owner's UUID, the garage id, the car id, R2 object keys and the owner's location.

A public read is refused (410) when the link is paused, revoked, or **its owner is banned**
— `BannedUserInterceptor` only sees authenticated requests, so `getPublicCar` runs that
check itself via `ProfileService.isBanned`.

Visits are counted inline, in the database, split into `view_count` and `qr_scan_count` by
the URL's `?s=` tag. Known link-preview crawlers (`internal/share/CrawlerUserAgents`) are
served in full but not counted: one WhatsApp link sent to a group of forty produces forty
fetches before a human has tapped anything.

## Transfers

**Whoever implements car transfer (marketplace) must call
`GarageService.revokeShareLinksForCar(carId)` inside the transfer transaction.** Nothing
calls it over HTTP today; it exists only as that seam. Without it, a QR sticker glued to a
sold car keeps pointing strangers at the new owner's build under the old owner's name. The
new owner's first "Share" then mints a fresh code, and the old sticker answers 410 —
which still advertises the app.

There is deliberately **no regenerate action** for owners. The code may already be printed
on a physical sticker; a button that silently invalidates it is a foot-gun, not a feature.
Pause and resume cover the real need, and they keep the code.

Modification prices have a per-mod check: when the viewer is not the owner and the mod's
`isPricePublic` is false, `price` (and its currency) is returned as `null` regardless of the
stored value. The flag defaults to **false**, so a price is the owner's own record unless they
publish it, and the rule holds on all three read paths: `getCar`, the public car page, and the
`ModShareCardDto` a shared mod draws in the feed. The owner always sees their own price.

## Cross-module dependencies

- **`profile.ProfileService`** — `findIdByUsername` for username resolution.
- **`profile.exception.ProfileNotFoundException`** — thrown when a target username
  cannot be resolved.
- **`profile.ProfileService`** — `findPublicProfileById` and `isBanned` for the public car
  page's owner card and its ban gate. The badges on that card arrive nested inside
  `PublicProfileDto`, so `garage` needs no direct edge to `badges`.
- **`storage.StorageService`** — `createUploadUrl(path)` and `createDownloadUrl(path)` to
  generate presigned Supabase Storage URLs. The garage module owns path construction and
  ownership validation; the storage module only handles the HTTP calls.
