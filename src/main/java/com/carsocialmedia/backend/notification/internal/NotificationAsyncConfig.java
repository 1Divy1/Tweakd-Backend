package com.carsocialmedia.backend.notification.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables Spring's asynchronous method execution so the notification producers' {@code
 * @ApplicationModuleListener}s run off the request thread (after the producing transaction commits).
 * Async is global once enabled anywhere; the notification module is the only consumer of it today.
 */
@Configuration
@EnableAsync
class NotificationAsyncConfig {
}
