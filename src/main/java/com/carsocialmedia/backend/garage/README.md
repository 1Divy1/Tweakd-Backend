# garage module

Manages each user's garage (one per user) and the cars and modifications inside it.
Owns the `garages`, `cars`, `car_modifications` and the seven car-reference lookup
tables (`car_brands`, `car_models`, `car_drivetrain_options`, `car_color_options`,
`car_distance_units`, `car_status_options`, `car_mod_categories`).

Depends on the `profile` module for username resolution and privacy flags, and on
the `follow` module for the accepted-follower check used to gate access to private
users' garages.

## Public API

### Service interface — `GarageService`

| Method | Description |
|---|---|
| `getMyGarage(currentUserId)` | The caller's own garage with car summaries |
| `getGarageByUsername(currentUserId, username)` | Another user's garage; private profiles gated by follow status |
| `addCar(currentUserId, CarRequest)` | Creates a car in the caller's garage |
| `updateCar(currentUserId, carId, CarRequest)` | Full replacement of an owned car |
| `deleteCar(currentUserId, carId)` | Deletes an owned car; mods cascade |
| `getCar(currentUserId, carId)` | Car detail including modifications; privacy-gated |
| `addModification(currentUserId, carId, CarModificationRequest)` | Adds a mod to an owned car |
| `updateModification(currentUserId, carId, modificationId, CarModificationRequest)` | Full replacement of an owned mod |
| `deleteModification(currentUserId, carId, modificationId)` | Deletes an owned mod |
| `listBrands()` / `listModelsByBrand(brandId)` / `listDrivetrains()` / `listColors()` / `listDistanceUnits()` / `listStatusOptions()` / `listModCategories()` | Reference data for client dropdowns |

### DTOs / records

| Type | Used for |
|---|---|
| `GarageDto` | Garage view with embedded list of `CarSummaryDto` |
| `CarSummaryDto` | Compact list item (brand, model, year, cover image) |
| `CarDto` | Full car detail with embedded `CarModificationDto` list |
| `CarModificationDto` | A single modification |
| `CarRequest` | Create / replace payload for a car (validated) |
| `CarModificationRequest` | Create / replace payload for a modification (validated; enforces `isPricePublic ⇒ price != null` via `@AssertTrue`) |
| `CarBrandDto`, `CarModelDto`, `CarDrivetrainDto`, `CarColorDto`, `CarDistanceUnitDto`, `CarStatusOptionDto`, `CarModCategoryDto` | Reference data |

### Exceptions

| Exception | HTTP | Trigger |
|---|---|---|
| `GarageNotFoundException` | 404 | A user's garage row is missing (should not happen if the `on_profile_created_create_garage` trigger is active) |
| `CarNotFoundException` | 404 | Car id doesn't exist |
| `CarModificationNotFoundException` | 404 | Modification id doesn't exist (or doesn't belong to the path's car id) |
| `NotCarOwnerException` | 403 | Caller is not the owner of the car they're trying to mutate |
| `PrivateGarageException` | 403 | Caller is viewing a private user's garage without being an accepted follower |
| `InvalidReferenceException` | 400 | Request references an unknown brand / model / drivetrain / color / unit / category, or a model that doesn't belong to the brand id provided |

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
| POST | `/cars` | Add a car to your garage (201) |
| PUT | `/cars/{carId}` | Replace your car |
| DELETE | `/cars/{carId}` | Delete your car (204) |
| GET | `/cars/{carId}` | Get car detail with modifications (privacy-gated) |

### Modifications

| Method | Path | Description |
|---|---|---|
| POST | `/cars/{carId}/modifications` | Add a mod to your car (201) |
| PUT | `/cars/{carId}/modifications/{modificationId}` | Replace one of your mods |
| DELETE | `/cars/{carId}/modifications/{modificationId}` | Delete one of your mods (204) |

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
| name | String | Nullable |
| createdAt | Instant | DB default `now()`; `insertable=false, updatable=false` |

### `CarEntity` → `cars`

`@ManyToOne(LAZY)` to `GarageEntity`, `CarBrandEntity`, `CarModelEntity`,
`CarDrivetrainEntity`, `CarColorEntity`, `CarDistanceUnitEntity`. Scalars: year,
horsepower, torque, weight, engineDisplacement, optional `zeroToOneHundred`,
`chassisCode`, `engineCode`, `coverImageUrl`. `createdAt` is DB-managed.

Schema-derived id (`gen_random_uuid()` default in Supabase) is set by the application
with `UUID.randomUUID()`. `@DynamicUpdate` keeps PATCH-style updates lean.

### `CarModificationEntity` → `car_modifications`

`@ManyToOne(LAZY)` to `CarEntity` and `CarModCategoryEntity`. Scalars match the DB
schema. Application enforces `isPricePublic ⇒ price != null` at validation time;
the DB enforces the same via `car_modifications_price_visibility_check`.

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
