package io.github.chalsense.server.config;

import io.github.chalsense.core.SecureRandomTokenGenerator;
import io.github.chalsense.core.TokenGenerator;
import io.github.chalsense.core.challenge.ChallengeCreator;
import io.github.chalsense.core.challenge.slider.BackgroundImageSource;
import io.github.chalsense.core.challenge.slider.BoundedRandom;
import io.github.chalsense.core.challenge.slider.SliderPuzzleGenerator;
import io.github.chalsense.core.security.SecurityEventSink;
import io.github.chalsense.core.ratelimit.RateLimiter;
import io.github.chalsense.core.site.SitePolicy;
import io.github.chalsense.core.site.SiteRegistration;
import io.github.chalsense.core.site.SiteRegistry;
import io.github.chalsense.core.site.WebOrigin;
import io.github.chalsense.core.state.StateStore;
import io.github.chalsense.core.ticket.TicketConsumer;
import io.github.chalsense.core.verify.ChallengeVerifier;
import io.github.chalsense.protocol.ActionName;
import io.github.chalsense.protocol.SiteKey;
import io.github.chalsense.server.security.ServiceCredentialAuthenticator;
import io.github.chalsense.server.security.StaticServiceCredentialAuthenticator;
import io.github.chalsense.store.redis.JedisStateStore;
import io.github.chalsense.store.redis.RedisChallengeResourceStore;
import io.github.chalsense.store.redis.RedisKeyspace;
import io.github.chalsense.store.redis.RedisRateLimiter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import redis.clients.jedis.RedisClient;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.net.InetAddress;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChalSenseServerProperties.class)
public class ServerConfiguration {
    @Bean(destroyMethod = "close")
    RedisClient redisClient(ChalSenseServerProperties properties) {
        URI uri = URI.create(properties.getRedisUri());
        if (!("redis".equals(uri.getScheme()) || "rediss".equals(uri.getScheme()))) {
            throw new IllegalArgumentException("chalsense.redis-uri must use redis or rediss");
        }
        return RedisClient.create(uri);
    }

    @Bean
    RedisKeyspace redisKeyspace(ChalSenseServerProperties properties) {
        return new RedisKeyspace(properties.getRedisNamespace());
    }

    @Bean
    StateStore stateStore(RedisClient client, RedisKeyspace keyspace) {
        return new JedisStateStore(client, keyspace);
    }

    @Bean
    RedisChallengeResourceStore resourceStore(
            RedisClient client, RedisKeyspace keyspace, SecureRandom secureRandom) {
        return new RedisChallengeResourceStore(client, keyspace, secureRandom, "/v1/public/resources");
    }

    @Bean
    RateLimiter rateLimiter(RedisClient client, RedisKeyspace keyspace,
            ChalSenseServerProperties properties, Environment environment) {
        boolean enabled = properties.getRateLimit().isEnabled();
        String address = environment.getProperty("server.address", "127.0.0.1");
        try {
            if (!enabled && !InetAddress.getByName(address).isLoopbackAddress()) {
                throw new IllegalArgumentException("public rate limiting must be enabled before binding a non-loopback address");
            }
        } catch (java.net.UnknownHostException exception) {
            throw new IllegalArgumentException("server.address must resolve during startup", exception);
        }
        return new RedisRateLimiter(client, keyspace);
    }

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TokenGenerator tokenGenerator(SecureRandom random) {
        return new SecureRandomTokenGenerator(random);
    }

    @Bean
    SiteRegistry siteRegistry(ChalSenseServerProperties properties) {
        Map<SiteKey, SiteRegistration> registrations = new HashMap<>();
        for (ChalSenseServerProperties.Site configured : properties.getSites()) {
            SiteKey key = new SiteKey(configured.getSiteKey());
            Set<ActionName> actions = configured.getAllowedActions().stream().map(ActionName::new)
                    .collect(Collectors.toUnmodifiableSet());
            Set<WebOrigin> origins = configured.getAllowedOrigins().stream().map(WebOrigin::parse)
                    .collect(Collectors.toUnmodifiableSet());
            SitePolicy policy = new SitePolicy(configured.getChallengeTtl(), configured.getTicketTtl(),
                    configured.getPolicyVersion(), actions, origins, configured.isAllowInsecureLoopbackOrigins());
            SiteRegistration registration = new SiteRegistration(key, configured.getDisplayName(),
                    configured.getStatus(), policy);
            if (registrations.put(key, registration) != null) {
                throw new IllegalArgumentException("siteKey must be unique");
            }
        }
        if (registrations.isEmpty()) throw new IllegalArgumentException("at least one chalsense site is required");
        Map<SiteKey, SiteRegistration> immutable = Map.copyOf(registrations);
        return key -> java.util.Optional.ofNullable(immutable.get(key));
    }

    @Bean
    BackgroundImageSource backgroundImageSource(ChalSenseServerProperties properties, SecureRandom random) {
        return new DirectoryBackgroundImageSource(properties.getBackgroundDirectory(), random);
    }

    @Bean
    SliderPuzzleGenerator challengeGenerator(
            BackgroundImageSource source, RedisChallengeResourceStore resources,
            SecureRandom random, ChalSenseServerProperties properties) {
        return new SliderPuzzleGenerator(source, resources, BoundedRandom.secure(random),
                properties.getMaximumConcurrentGenerations());
    }

    @Bean
    ChallengeCreator challengeCreator(StateStore store, SiteRegistry sites, Clock clock,
            TokenGenerator tokens, SliderPuzzleGenerator generator) {
        return new ChallengeCreator(store, sites, clock, tokens, generator, SecurityEventSink.noop());
    }

    @Bean
    ChallengeVerifier challengeVerifier(StateStore store, SiteRegistry sites, Clock clock, TokenGenerator tokens) {
        return new ChallengeVerifier(store, sites, clock, tokens, SecurityEventSink.noop());
    }

    @Bean
    TicketConsumer ticketConsumer(StateStore store, SiteRegistry sites, Clock clock) {
        return new TicketConsumer(store, sites, clock, SecurityEventSink.noop());
    }

    @Bean
    ServiceCredentialAuthenticator serviceCredentialAuthenticator(ChalSenseServerProperties properties) {
        return new StaticServiceCredentialAuthenticator(properties);
    }
}
