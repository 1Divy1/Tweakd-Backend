package com.tweakdapp.backend.testsupport;

import com.tweakdapp.backend.profile.internal.BanEnforcementTestStub;
import com.tweakdapp.backend.shared.security.SecurityConfig;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This app's flavor of {@link WebMvcTest}: the slice plus the real {@link SecurityConfig}
 * (route rules, JWT role mapping) and the {@link BanEnforcementTestStub} that satisfies the
 * globally registered banned-user interceptor. Use it for every controller test:
 *
 * <pre>{@code @AppWebMvcTest(ProfileController.class)}</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@WebMvcTest
@Import({SecurityConfig.class, BanEnforcementTestStub.class})
public @interface AppWebMvcTest {

    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};
}
