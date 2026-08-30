package com.tweakdapp.backend.notification.internal.push;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "firebase")
@Getter
@Setter
public class FirebaseProperties {

    /** Set false to disable push delivery entirely (tests, local dev without credentials). */
    private boolean enabled = true;

    /** Firebase project id. Optional — Application Default Credentials usually carry it. */
    private String projectId;

    /** Android notification channel the app has registered. Must match the Flutter side. */
    private String androidChannelId = "tweakd_default";

    /** When true, messages are validated by FCM but never delivered. Useful in staging. */
    private boolean dryRun = false;
}
