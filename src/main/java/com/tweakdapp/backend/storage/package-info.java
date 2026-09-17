@ApplicationModule(
        displayName = "Storage",
        // Still a leaf as far as feature modules go — it knows nothing about garage, posts or
        // events. The one dependency is `shared`, the OPEN module for cross-cutting concerns:
        // StorageController carries @RateLimited, because minting presigned upload URLs is the
        // endpoint group where abuse costs real R2 storage and bandwidth.
        allowedDependencies = "shared"
)
package com.tweakdapp.backend.storage;

import org.springframework.modulith.ApplicationModule;