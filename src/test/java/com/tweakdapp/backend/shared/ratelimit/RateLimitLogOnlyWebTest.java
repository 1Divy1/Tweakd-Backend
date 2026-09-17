package com.tweakdapp.backend.shared.ratelimit;

import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import com.tweakdapp.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code LOG_ONLY} — the mode the limiter ships in — must never block. It only records what it
 * would have blocked, which is the data the limits get tuned from before enforcement.
 *
 * <p>This is also the guard for the other 26 controller tests: they run with the real limiter
 * mounted at its default mode, so if this mode ever started rejecting, the whole suite would go
 * red for the wrong reason.
 */
@AppWebMvcTest(RateLimitLogOnlyWebTest.ProbeController.class)
@Import(RateLimitLogOnlyWebTest.ProbeController.class)
@TestPropertySource(properties = {
        "rate-limit.mode=LOG_ONLY",
        "rate-limit.general.capacity=1",
        "rate-limit.general.refill-tokens=1",
        "rate-limit.general.refill-period=1h",
        "rate-limit.limits.reports.capacity=1",
        "rate-limit.limits.reports.refill-tokens=1",
        "rate-limit.limits.reports.refill-period=1h",
})
class RateLimitLogOnlyWebTest {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    static class ProbeController {

        @GetMapping("/api/v1/probe")
        String probe() {
            return "ok";
        }

        @GetMapping("/api/v1/probe/limited")
        @RateLimited(RateLimits.REPORTS)
        String limited() {
            return "ok";
        }
    }

    @Test
    void overTheGeneralLimitStillPassesThrough() throws Exception {
        UUID user = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/probe").with(TestJwts.user(user)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void overANamedEndpointLimitStillPassesThrough() throws Exception {
        UUID user = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/probe/limited").with(TestJwts.user(user)))
                    .andExpect(status().isOk());
        }
    }
}
