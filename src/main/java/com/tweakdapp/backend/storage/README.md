# Storage

Mints presigned R2 PUT URLs and turns stored object keys into public URLs. No file bytes pass
through the backend: Flutter uploads straight to R2, then sends the returned `key` to the module
that owns the resource.

## Upload access

A presigned URL is a bearer credential for one object key, so it is only handed to someone allowed
to write that resource:

| Endpoint | Key | Who may mint |
|---|---|---|
| `GET /api/storage/avatar` | `avatars/{userId}/{uuid}.webp` | the caller (id from the JWT) |
| `GET /api/storage/cars/{carId}/cover` | `cars/{carId}/cover/{uuid}.webp` | car owner |
| `GET /api/storage/cars/{carId}/gallery` | `cars/{carId}/gallery/{uuid}.webp` | car owner |
| `GET /api/storage/cars/{carId}/modifications/{modId}`, `POST .../upload-urls` | `cars/{carId}/modifications/{modId}/{phase}/{uuid}.{ext}` | car owner |
| `POST /api/storage/posts/{postId}/upload-urls` | `posts/{postId}/{uuid}.webp` | post author |
| `GET /api/storage/events/{eventId}/cover` | `events/{eventId}/{uuid}.webp` | event organizer |

Storage has no module dependencies, so it cannot look up cars, posts or events itself. Owning
modules implement `UploadAccessPolicy` for their `UploadTarget`; `StorageServiceImpl` calls it
before minting and fails closed when no policy is registered for a target.

Minting is only half of it. Every attach endpoint (car cover, car gallery, modification media, post
images, event cover, avatar) also rejects a key outside the resource's own prefix unless it is
already stored on that resource. Otherwise a caller could attach another user's object and have it
deleted from R2 on their next edit.

Every key has a random component, so new media never overwrites an object that is live.
