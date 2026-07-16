package com.carsocialmedia.backend.presence.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables scheduling for the presence sweeps ({@link PresenceLifecycle}). Scheduling is global once
 * enabled anywhere; this is currently the only module that needs it.
 */
@Configuration
@EnableScheduling
class PresenceConfig {
}
