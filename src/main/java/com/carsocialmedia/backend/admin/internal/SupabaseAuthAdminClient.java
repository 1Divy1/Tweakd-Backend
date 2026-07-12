package com.carsocialmedia.backend.admin.internal;

import com.carsocialmedia.backend.admin.exception.StaffEmailInUseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.UUID;

/**
 * The GoTrue admin API, called with the service-role key ({@code SUPABASE_SECRET_KEY}) — the
 * backend's only credentialed write path into {@code auth.users}. Staff accounts are created here
 * and nowhere else: {@link #inviteStaff} sends Supabase's invite email and marks the new auth user
 * so that (a) the {@code handle_new_user} trigger skips the app-profile insert ({@code is_staff}
 * in the user metadata, present already at INSERT time) and (b) the {@code /api/v1/admin/**}
 * security gate opens ({@code app_metadata.role = 'admin'}, patched right after).
 */
@Component
class SupabaseAuthAdminClient {

    private final RestClient restClient;

    SupabaseAuthAdminClient(@Value("${supabase.url}") String supabaseUrl,
                            @Value("${supabase.secret-key}") String secretKey) {
        this.restClient = RestClient.builder()
                .baseUrl(supabaseUrl + "/auth/v1")
                .defaultHeader("apikey", secretKey)
                .defaultHeader("Authorization", "Bearer " + secretKey)
                .build();
    }

    /**
     * Creates the staff auth user and sends the invite email; returns the new user's UUID.
     * 409s when the email already belongs to an auth user (app account or fellow staff).
     */
    UUID inviteStaff(String email, String displayName) {
        try {
            InvitedUser user = restClient.post()
                    .uri("/invite")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "email", email,
                            "data", Map.of("is_staff", true, "display_name", displayName)))
                    .retrieve()
                    .body(InvitedUser.class);
            if (user == null || user.id() == null) {
                throw new IllegalStateException("Supabase invite returned no user id for " + email);
            }
            grantAdminRole(user.id());
            return user.id();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 422 || e.getStatusCode().value() == 400) {
                throw new StaffEmailInUseException(email);
            }
            throw e;
        }
    }

    /** Deletes the staff auth user; their team row follows via the FK's ON DELETE CASCADE. */
    void deleteUser(UUID userId) {
        restClient.delete()
                .uri("/admin/users/{id}", userId)
                .retrieve()
                .toBodilessEntity();
    }

    private void grantAdminRole(UUID userId) {
        restClient.put()
                .uri("/admin/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("app_metadata", Map.of("role", "admin")))
                .retrieve()
                .toBodilessEntity();
    }

    private record InvitedUser(UUID id) {
    }
}
