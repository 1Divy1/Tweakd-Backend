# garage module

Manages each user's garage (one per user) and the cars and modifications inside it.
Owns the `garages`, `cars`, `car_modifications`, `dream_cars` and the seven
car-reference lookup tables (`car_brands`, `car_models`, `car_drivetrain_options`,
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
| `CarSummaryDto` | Compact list item (brand, model, year, cover image) |
| `CarDto` | Full car detail with embedded `CarModificationDto` list |
| `CarModificationDto` | A single modification |
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
| `CarBrandDto`, `CarModelDto`, `CarDrivetrainDto`, `CarColorDto`, `CarDistanceUnitDto`, `CarStatusOptionDto`, `CarModCategoryDto` | Reference data |
| `DreamCarDto` | A dream car with denormalized brand/model names, createdAt |
| `DreamCarRequest` | Create payload: a non-empty `dreamCars` list of `DreamCarRequestBody` |
| `DreamCarRequestBody` | A single dream car: `brandId` (required), `modelId` (optional). Also the body of a single-item update |

### Exceptions

| Exception | HTTP | Trigger |
|---|---|---|
| `GarageNotFoundException` | 404 | A user's garage row is missing (should not happen if the `on_profile_created_create_garage` trigger is active) |
| `CarNotFoundException` | 404 | Car id doesn't exist |
| `CarModificationNotFoundException` | 404 | Modification id doesn't exist (or doesn't belong to the path's car id) |
| `CarImageNotFoundException` | 404 | Gallery image id doesn't exist or doesn't belong to the path's car id |
| `NotCarOwnerException` | 403 | Caller is not the owner of the car they're trying to mutate |
| `DreamCarNotFoundException` | 404 | Dream car id doesn't exist or isn't owned by the caller (queries are owner-scoped) |
| `PrivateGarageException` | 403 | Caller is viewing a private user's garage without being an accepted follower |
| `InvalidReferenceException` | 400 | Request references an unknown brand / model / drivetrain / color / unit / category, or a model that doesn't belong to the brand id provided |
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

> Upload URLs for cover, modification before/after, and gallery slots are issued by
> `POST /cars` as part of car creation. Clients PUT file bytes directly to the returned
> Supabase URLs — no bytes travel through the backend.

### Dream cars

| Method | Path | Description |
|---|---|---|
| GET | `/dream-cars` | Your dream cars (oldest first) |
| POST | `/dream-cars` | Add one or more dream cars from a `dreamCars` list (201) |
| PUT | `/dream-cars/{dreamCarId}` | Update one of your dream cars (single `DreamCarRequestBody`) |
| DELETE | `/dream-cars/{dreamCarId}` | Remove one of your dream cars (204) |

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

## Privacy rules

`getGarageByUsername` and `getCar` (when viewer ≠ owner) enforce the same rule used
by the follow module's social-graph endpoints:

1. Owner viewing themselves → always allowed.
2. Owner is a **public** profile → allowed.
3. Owner is **private** AND viewer is an accepted follower → allowed.
4. Otherwise → `PrivateGarageException` (403).

Modification prices have an additional check: when the viewer is not the owner and
the mod's `isPricePublic` is false, `price` is returned as `null` regardless of the
stored value.

## Cross-module dependencies

- **`profile.ProfileService`** — `findIdByUsername` for username resolution and
  `isPrivate(UUID)` to apply the privacy gate.
- **`profile.exception.ProfileNotFoundException`** — thrown when a target username
  cannot be resolved.
- **`follow.FollowService.isAcceptedFollower(viewerId, targetId)`** — used to grant
  access to private users' garages.
- **`storage.StorageService`** — `createUploadUrl(path)` and `createDownloadUrl(path)` to
  generate presigned Supabase Storage URLs. The garage module owns path construction and
  ownership validation; the storage module only handles the HTTP calls.
