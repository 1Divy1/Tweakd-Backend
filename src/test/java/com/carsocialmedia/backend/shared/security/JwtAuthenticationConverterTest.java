package com.carsocialmedia.backend.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The role-mapping rules of {@link SecurityConfig#jwtAuthenticationConverter()}:
 * {@code app_metadata.role} becomes {@code ROLE_<UPPERCASE>}, absent claim falls back to ROLE_USER.
 *
 * <p>Assertions look only at {@code ROLE_*} authorities: Spring Security 7 additionally attaches
 * a {@code FACTOR_BEARER} authority to every JWT authentication, which is framework behavior,
 * not ours.
 */
class JwtAuthenticationConverterTest {

    private final JwtAuthenticationConverter converter = new SecurityConfig().jwtAuthenticationConverter();

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("00000000-0000-0000-0000-000000000001");
    }

    private static String soleAuthority(AbstractAuthenticationToken auth) {
        var roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .toList();
        assertThat(roles).hasSize(1);
        return roles.getFirst();
    }

    @Test
    void missingAppMetadataMapsToRoleUser() {
        AbstractAuthenticationToken auth = converter.convert(jwt().build());

        assertThat(soleAuthority(auth)).isEqualTo("ROLE_USER");
    }

    @Test
    void appMetadataWithoutRoleMapsToRoleUser() {
        Jwt token = jwt().claim("app_metadata", Map.of("provider", "email")).build();

        AbstractAuthenticationToken auth = converter.convert(token);

        assertThat(soleAuthority(auth)).isEqualTo("ROLE_USER");
    }

    @Test
    void adminRoleMapsToRoleAdmin() {
        Jwt token = jwt().claim("app_metadata", Map.of("role", "admin")).build();

        AbstractAuthenticationToken auth = converter.convert(token);

        assertThat(soleAuthority(auth)).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void roleIsUppercased() {
        Jwt token = jwt().claim("app_metadata", Map.of("role", "Admin")).build();

        AbstractAuthenticationToken auth = converter.convert(token);

        assertThat(soleAuthority(auth)).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void principalNameIsTheJwtSubject() {
        AbstractAuthenticationToken auth = converter.convert(jwt().build());

        assertThat(auth.getName()).isEqualTo("00000000-0000-0000-0000-000000000001");
    }
}
