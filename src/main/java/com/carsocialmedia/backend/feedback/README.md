# feedback module

Owns user-submitted app feedback — bug reports, feature requests, and general notes — plus the
reference tables that back the pickers and the status lifecycle (`feedback_type_options`,
`feedback_feature_options`, `feedback_status_options`).

Unlike the `report` module, `feedback` is a **self-contained leaf**: a feedback row references its
author only by a flat `profiles.id` UUID (the JWT subject) and needs no cross-module validation, so
the module owns **all** of its own REST endpoints without risking a Spring Modulith cycle. It depends
only on `shared`.

## Public API — `FeedbackService`

| Method | Description |
|---|---|
| `submitFeedback(userId, request)` | Store one feedback (validates `type`, and `feature` when present) |
| `listFeedbackTypes()` | Feedback categories for the type picker |
| `listFeedbackFeatures()` | App features for the feature picker |
| `listMyFeedback(userId)` | Every feedback the user submitted, newest first — for their "my feedback" view |

`userId` is the JWT subject and is trusted to exist. Validation is intentionally light: `content` and
`type` are required; `feature` is optional (e.g. feedback about something not yet a listed feature)
and `reproductionSteps` is optional (the mobile client only collects it for the `bug` type, but the
backend accepts it for any type). Blank optional strings are normalized to `null`.

### DTOs (`feedback.dto`)

| Type | Used for |
|---|---|
| `FeedbackRequest(content, type, feature, reproductionSteps)` | Request body for `POST /feedback`; `content` + `type` are `@NotBlank` |
| `FeedbackTypeDto(id, type)` | One feedback category for the type picker |
| `FeedbackFeatureDto(id, name)` | One app feature for the feature picker |
| `FeedbackStatusDto(id, name, color)` | A feedback's lifecycle state, resolved for the chip |
| `MyFeedbackDto(id, content, type, feature, reproductionSteps, response, status, createdAt)` | One entry in the user's own feedback feed (`type`/`feature` resolved to labels, `status` to a `FeedbackStatusDto`; `response` is the moderator reply, `null` until one exists) |

### Exceptions

| Exception | HTTP | Trigger |
|---|---|---|
| `InvalidFeedbackTypeException` | 400 | `type` doesn't reference a `feedback_type_options` row |
| `InvalidFeedbackFeatureException` | 400 | `feature` is given but doesn't reference a `feedback_feature_options` row |

Blank `content` / `type` are rejected as 400 by bean validation (`@NotBlank`) via the shared
`GlobalExceptionHandler`.

## REST endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/feedback` | Submit feedback → `204 No Content` |
| GET | `/api/v1/feedback/types` | List feedback categories |
| GET | `/api/v1/feedback/features` | List selectable features |
| GET | `/api/v1/feedback/mine` | The caller's own submitted feedback, newest first |

All endpoints require authentication; `/mine` is always scoped to the JWT subject.

## Entities

| Entity → table | Notes |
|---|---|
| `FeedbackEntity` → `feedback` | `id` (app-generated UUID); `userId` / `type` / `feature` / `status` are flat references; `response` is the nullable moderator reply; `status` and `createdAt` DB-managed |
| `FeedbackTypeOptionEntity` → `feedback_type_options` | Seeded reference data (text id) |
| `FeedbackFeatureOptionEntity` → `feedback_feature_options` | Seeded reference data (text id) |
| `FeedbackStatusOptionEntity` → `feedback_status_options` | Seeded lifecycle states (id, name, sort_order, color) |

`created_at` is DB-managed (`insertable = false`). `status` is also DB-managed on insert
(`insertable = false`) so its column DEFAULT `'submitted'` fires; moderators advance it (and write
`response`) afterwards — the app has no endpoint to mutate either yet. The `feedback.id` UUID is
generated in Java (`UUID.randomUUID()`) before insert, matching the rest of the codebase (schema is
owned by Supabase, not Hibernate).
