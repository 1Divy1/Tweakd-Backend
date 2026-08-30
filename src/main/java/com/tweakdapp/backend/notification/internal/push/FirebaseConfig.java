package com.tweakdapp.backend.notification.internal.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Boots the Firebase Admin SDK from Application Default Credentials — on Cloud Run that is the
 * runtime service account (no key material in the image or the repo); locally it falls back to the
 * {@code GOOGLE_APPLICATION_CREDENTIALS} env var or {@code gcloud auth application-default login}.
 *
 * <p>Credential resolution failure is deliberately <em>not</em> fatal. Push is a best-effort side
 * channel on top of notifications that are already durably stored and readable over REST, so a
 * missing credential must degrade to "no push", never to "the service will not start" — which also
 * keeps the test suite and a fresh checkout runnable with no Firebase setup at all.
 *
 * <p>This factory picks the {@link FcmSender} implementation itself rather than contributing a
 * nullable {@code FirebaseMessaging} bean, so nothing downstream has to reason about its absence.
 */
@Configuration
class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    private static final String APP_NAME = "tweakd-push";

    @Bean
    FcmSender fcmSender(FirebaseProperties properties) {
        FirebaseMessaging messaging = initialise(properties);
        if (messaging == null) {
            return new NoOpFcmSender();
        }
        return new FirebaseFcmSender(messaging, properties);
    }

    private static FirebaseMessaging initialise(FirebaseProperties properties) {
        if (!properties.isEnabled()) {
            log.info("Firebase push disabled (firebase.enabled=false); notifications will not be pushed");
            return null;
        }
        try {
            FirebaseApp app = FirebaseApp.getApps().stream()
                    .filter(existing -> APP_NAME.equals(existing.getName()))
                    .findFirst()
                    .orElseGet(() -> FirebaseApp.initializeApp(options(properties), APP_NAME));

            log.info("Firebase push initialised (project={}, dryRun={})",
                    app.getOptions().getProjectId(), properties.isDryRun());
            return FirebaseMessaging.getInstance(app);
        } catch (Exception e) {
            // Message only — a credential error can carry environment detail we do not want in logs.
            log.error("Firebase push unavailable, falling back to no-op sender: {}", e.getMessage());
            return null;
        }
    }

    private static FirebaseOptions options(FirebaseProperties properties) {
        try {
            FirebaseOptions.Builder builder = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault());
            if (properties.getProjectId() != null && !properties.getProjectId().isBlank()) {
                builder.setProjectId(properties.getProjectId());
            }
            return builder.build();
        } catch (Exception e) {
            throw new IllegalStateException("Could not resolve Application Default Credentials", e);
        }
    }
}
