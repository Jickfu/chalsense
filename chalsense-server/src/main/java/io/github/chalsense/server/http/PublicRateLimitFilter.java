package io.github.chalsense.server.http;

import io.github.chalsense.core.ratelimit.RateLimitOperation;
import io.github.chalsense.core.ratelimit.RateLimitPolicy;
import io.github.chalsense.core.ratelimit.RateLimitRequest;
import io.github.chalsense.core.ratelimit.RateLimitResult;
import io.github.chalsense.core.ratelimit.RateLimiter;
import io.github.chalsense.protocol.SiteKey;
import io.github.chalsense.server.config.ChalSenseServerProperties;
import io.github.chalsense.server.ratelimit.ClientNetworkKeyResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class PublicRateLimitFilter extends OncePerRequestFilter {
    private static final Pattern CREATE = Pattern.compile("/v1/public/sites/([A-Za-z0-9_-]{8,64})/challenges");
    private static final Pattern VERIFY = Pattern.compile(
            "/v1/public/sites/([A-Za-z0-9_-]{8,64})/challenges/[A-Za-z0-9_-]{22}/verify");
    private final RateLimiter limiter;
    private final boolean enabled;
    private final ClientNetworkKeyResolver clientKeys;
    private final Map<String, SitePolicies> policies;

    public PublicRateLimitFilter(
            RateLimiter limiter, ChalSenseServerProperties properties) {
        this.limiter = limiter;
        enabled = properties.getRateLimit().isEnabled();
        clientKeys = enabled ? new ClientNetworkKeyResolver(properties.getRateLimit()) : null;
        Map<String, SitePolicies> configured = new HashMap<>();
        for (ChalSenseServerProperties.Site site : properties.getSites()) {
            ChalSenseServerProperties.SiteRateLimit source = site.getRateLimit();
            SitePolicies value = new SitePolicies(
                    policy(source.getCreateClient()), policy(source.getCreateSite()),
                    policy(source.getVerifyClient()), policy(source.getVerifySite()));
            if (configured.put(site.getSiteKey(), value) != null) {
                throw new IllegalArgumentException("siteKey must be unique");
            }
        }
        policies = Map.copyOf(configured);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Target target = target(request);
        if (!enabled || target == null) {
            chain.doFilter(request, response);
            return;
        }
        SitePolicies site = policies.get(target.siteKey());
        if (site == null) {
            chain.doFilter(request, response);
            return;
        }
        final String clientKey;
        try {
            clientKey = clientKeys.resolve(request);
        } catch (IllegalArgumentException exception) {
            reject(response, 400, "INVALID_REQUEST", null);
            return;
        }
        RateLimitPolicy client = target.operation() == RateLimitOperation.CREATE
                ? site.createClient() : site.verifyClient();
        RateLimitPolicy global = target.operation() == RateLimitOperation.CREATE
                ? site.createSite() : site.verifySite();
        RateLimitResult result = limiter.acquire(new RateLimitRequest(
                new SiteKey(target.siteKey()), target.operation(), clientKey, client, global));
        if (result instanceof RateLimitResult.Allowed) {
            chain.doFilter(request, response);
        } else if (result instanceof RateLimitResult.Limited limited) {
            long seconds = Math.max(1, Math.floorDiv(limited.retryAfterMillis() + 999, 1000));
            reject(response, 429, "RATE_LIMITED", seconds);
        } else {
            reject(response, 503, "DEPENDENCY_UNAVAILABLE", null);
        }
    }

    private static Target target(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) return null;
        Matcher create = CREATE.matcher(request.getRequestURI());
        if (create.matches()) return new Target(create.group(1), RateLimitOperation.CREATE);
        Matcher verify = VERIFY.matcher(request.getRequestURI());
        return verify.matches() ? new Target(verify.group(1), RateLimitOperation.VERIFY) : null;
    }

    private static RateLimitPolicy policy(ChalSenseServerProperties.Limit source) {
        if (source == null || source.getInterval() == null) throw new IllegalArgumentException("rate limit policy is required");
        return new RateLimitPolicy(source.getBurst(), source.getInterval().toMillis());
    }

    private static void reject(HttpServletResponse response, int status, String code, Long retryAfter)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        if (retryAfter != null) response.setHeader("Retry-After", Long.toString(retryAfter));
        response.getWriter().write("{\"protocolVersion\":\"1\",\"error\":{\"code\":\"" + code
                + "\",\"requestId\":\"" + RequestIds.next() + "\"}}");
    }

    private record Target(String siteKey, RateLimitOperation operation) {}
    private record SitePolicies(
            RateLimitPolicy createClient, RateLimitPolicy createSite,
            RateLimitPolicy verifyClient, RateLimitPolicy verifySite) {}
}
