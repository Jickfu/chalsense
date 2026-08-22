package io.github.chalsense.server.http;

import io.github.chalsense.core.ratelimit.RateLimitRequest;
import io.github.chalsense.core.ratelimit.RateLimitResult;
import io.github.chalsense.core.ratelimit.RateLimiter;
import io.github.chalsense.server.config.ChalSenseServerProperties;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PublicRateLimitFilterTest {
    @Test
    void mapsLimitedAndUnavailableBeforeTheChain() throws Exception {
        FakeLimiter limiter = new FakeLimiter(new RateLimitResult.Limited(1001));
        PublicRateLimitFilter filter = new PublicRateLimitFilter(limiter, properties(true));
        MockHttpServletResponse limited = execute(filter, new MockFilterChain());
        assertEquals(429, limited.getStatus());
        assertEquals("2", limited.getHeader("Retry-After"));
        assertTrue(limited.getContentAsString().contains("RATE_LIMITED"));
        assertEquals("site_test", limiter.request.siteKey().value());

        limiter.result = new RateLimitResult.Unavailable();
        MockHttpServletResponse unavailable = execute(filter, new MockFilterChain());
        assertEquals(503, unavailable.getStatus());
        assertTrue(unavailable.getContentAsString().contains("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void allowsConfirmedRequestAndBypassesWhenDisabled() throws Exception {
        FakeLimiter allowed = new FakeLimiter(new RateLimitResult.Allowed());
        MockFilterChain chain = new MockFilterChain();
        assertEquals(200, execute(new PublicRateLimitFilter(allowed, properties(true)), chain).getStatus());
        assertNotNull(chain.getRequest());

        FakeLimiter unused = new FakeLimiter(new RateLimitResult.Unavailable());
        MockFilterChain disabledChain = new MockFilterChain();
        execute(new PublicRateLimitFilter(unused, properties(false)), disabledChain);
        assertNull(unused.request);
        assertNotNull(disabledChain.getRequest());
    }

    private static MockHttpServletResponse execute(PublicRateLimitFilter filter, MockFilterChain chain)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/v1/public/sites/site_test/challenges");
        request.setRemoteAddr("192.0.2.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private static ChalSenseServerProperties properties(boolean enabled) {
        ChalSenseServerProperties properties = new ChalSenseServerProperties();
        properties.getRateLimit().setEnabled(enabled);
        properties.getRateLimit().setHmacKey("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        ChalSenseServerProperties.Site site = new ChalSenseServerProperties.Site();
        site.setSiteKey("site_test");
        properties.setSites(List.of(site));
        return properties;
    }

    private static final class FakeLimiter implements RateLimiter {
        private RateLimitResult result;
        private RateLimitRequest request;
        private FakeLimiter(RateLimitResult result) { this.result = result; }
        @Override public RateLimitResult acquire(RateLimitRequest request) {
            this.request = request;
            return result;
        }
    }
}
