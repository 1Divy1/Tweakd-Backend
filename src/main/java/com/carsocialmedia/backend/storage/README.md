# storage module

Thin infrastructure module that wraps the **Supabase Storage REST API** to produce
presigned upload and download URLs. It carries no domain knowledge — callers (primarily
the `garage` module) are responsible for ownership validation and path construction.

## Public API

### Service interface — `StorageService`

| Method | Description |
|---|---|
| `createUploadUrl(path)` | Returns a Supabase presigned URL the client PUTs a file to directly |
| `createDownloadUrl(path)` | Returns a short-lived signed URL for reading an object |

Both methods accept an **object path** relative to the configured bucket (no leading slash).
Errors from the Supabase Storage API surface as `StorageServiceException` (internal; the
global handler maps uncaught runtime exceptions to 500).

## Configuration

Bound under the `supabase.storage` prefix via `@ConfigurationProperties`:

| Property | Description |
|---|---|
| `supabase.storage.bucket` | Supabase Storage bucket name |
| `supabase.storage.signed-url-ttl` | Lifetime of download URLs in seconds |
| `supabase.storage.service-role-key` | Supabase service-role JWT used to authenticate requests to the Storage REST API |

The `supabase.url` property (set via `.env`) is also required so the client can construct
absolute URLs from the relative paths Supabase returns.

## Internal structure

```
storage/
├── StorageService.java          ← public interface (the only exported type)
├── package-info.java
└── internal/
    ├── StorageServiceImpl.java  ← delegates to SupabaseStorageClient
    ├── SupabaseStorageClient.java  ← RestClient wrapper; calls /storage/v1/object/...
    ├── StorageProperties.java   ← @ConfigurationProperties binding
    └── exceptions/
        └── StorageServiceException.java  ← wraps RestClientException
```

## Upload flow

1. The `garage` module calls `createUploadUrl(path)` with a deterministic path it owns
   (e.g., `car-photos/{ownerId}/{carId}/cover`).
2. `SupabaseStorageClient` calls `POST /storage/v1/object/upload/sign/{bucket}/{path}`.
3. The resulting presigned URL is returned to the controller, which passes it to the client.
4. The client PUTs the file bytes directly to Supabase — no file bytes pass through the backend.

## Download flow

1. The `garage` module calls `createDownloadUrl(path)` after verifying the caller has
   permission to view the car.
2. `SupabaseStorageClient` calls `POST /storage/v1/object/sign/{bucket}/{path}` with the
   configured TTL.
3. The signed URL is returned to the client.

## Cross-module usage

Only the `garage` module uses this service. All other modules should go through `garage`
if they ever need car image URLs. Do not add domain logic here.