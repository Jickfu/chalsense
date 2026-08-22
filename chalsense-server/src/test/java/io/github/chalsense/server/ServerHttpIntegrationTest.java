package io.github.chalsense.server;

import io.github.chalsense.core.state.ChallengeState;
import io.github.chalsense.core.state.serialization.StateJsonCodec;
import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.SiteKey;
import io.github.chalsense.store.redis.RedisKeyspace;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import redis.clients.jedis.RedisClient;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ServerHttpIntegrationTest {
    private static final String SITE_KEY = "site_test";
    private static final String ORIGIN = "https://app.example.test";
    private static final String CONTEXT = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String SECRET = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final Pattern CHALLENGE_ID = Pattern.compile("\"challengeId\":\"([A-Za-z0-9_-]{22})\"");
    private static final Pattern TICKET = Pattern.compile("\"verificationTicket\":\"([A-Za-z0-9_-]{43})\"");
    private static final Pattern RESOURCE_URL = Pattern.compile("\"url\":\"([^\"]+)\"");

    private static RedisClient redis;
    private static RedisKeyspace keyspace;
    private static ConfigurableApplicationContext first;
    private static ConfigurableApplicationContext second;
    private static Path imageDirectory;
    private static HttpClient http;
    private static URI firstBase;
    private static URI secondBase;
    private static String redisUri;

    @BeforeAll
    static void startServers() throws Exception {
        assumeTrue(Boolean.getBoolean("chalsense.server.integration"),
                "set -Dchalsense.server.integration=true with a dedicated Redis/Valkey server");
        String host = System.getProperty("chalsense.redis.host", "127.0.0.1");
        int port = Integer.getInteger("chalsense.redis.port", 6379);
        redisUri = System.getProperty("chalsense.redis.uri", "redis://" + host + ":" + port);
        redis = RedisClient.create(URI.create(redisUri));
        assertEquals("PONG", redis.ping());
        keyspace = new RedisKeyspace("httpit" + UUID.randomUUID().toString().replace("-", ""));
        imageDirectory = Files.createTempDirectory("chalsense-http-it-");
        writeOwnedFixture(imageDirectory.resolve("background.png"));
        Map<String, Object> properties = properties();
        first = start(properties);
        second = start(properties);
        firstBase = base(first);
        secondBase = base(second);
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @AfterAll
    static void stopServers() throws Exception {
        if (second != null) second.close();
        if (first != null) first.close();
        if (redis != null) {
            if (keyspace != null) {
                for (String key : redis.keys(keyspace.namespace() + ":*")) redis.del(key);
            }
            redis.close();
        }
        if (imageDirectory != null && Files.exists(imageDirectory)) {
            try (var paths = Files.walk(imageDirectory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void completesCrossInstanceHttpFlowAndRejectsConcurrentReplays() throws Exception {
        HttpResponse<byte[]> created = post(firstBase,
                "/v1/public/sites/" + SITE_KEY + "/challenges",
                "{\"protocolVersion\":\"1\",\"action\":\"login\",\"contextDigest\":\"" + CONTEXT + "\"}",
                Map.of("Origin", ORIGIN));
        assertEquals(200, created.statusCode());
        assertEquals(ORIGIN, created.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        String createJson = text(created);
        String challengeId = capture(CHALLENGE_ID, createJson);
        List<String> resources = captures(RESOURCE_URL, createJson);
        assertEquals(2, resources.size());

        for (String resource : resources) {
            HttpResponse<byte[]> firstRead = get(secondBase, resource);
            HttpResponse<byte[]> repeatedRead = get(secondBase, resource);
            assertEquals(200, firstRead.statusCode());
            assertEquals("image/png", firstRead.headers().firstValue("Content-Type").orElseThrow());
            assertArrayEquals(firstRead.body(), repeatedRead.body());
            assertTrue(firstRead.body().length > 0);
        }

        byte[] encodedState = redis.get(keyspace.challengeKey(new SiteKey(SITE_KEY), new ChallengeId(challengeId)));
        assertNotNull(encodedState);
        ChallengeState state = new StateJsonCodec().decodeChallenge(encodedState);
        long finalPieceX = state.geometry().pieceTargetX();
        long trackX = finalPieceX - state.geometry().pieceStartX();
        String verifyBody = "{\"protocolVersion\":\"1\",\"solution\":{\"finalPieceX\":" + finalPieceX
                + ",\"track\":[{\"x\":0,\"y\":0,\"t\":0,\"event\":\"START\"},"
                + "{\"x\":" + trackX + ",\"y\":0,\"t\":400,\"event\":\"END\"}]}}";
        String verifyPath = "/v1/public/sites/" + SITE_KEY + "/challenges/" + challengeId + "/verify";
        CompletableFuture<HttpResponse<byte[]>> verifyFirst = postAsync(firstBase, verifyPath, verifyBody,
                Map.of("Origin", ORIGIN));
        CompletableFuture<HttpResponse<byte[]>> verifySecond = postAsync(secondBase, verifyPath, verifyBody,
                Map.of("Origin", ORIGIN));
        List<HttpResponse<byte[]>> verificationResponses = List.of(verifyFirst.join(), verifySecond.join());
        assertEquals(List.of(200, 409), verificationResponses.stream().map(HttpResponse::statusCode).sorted().toList());
        String ticket = verificationResponses.stream().filter(response -> response.statusCode() == 200)
                .map(ServerHttpIntegrationTest::text).map(json -> capture(TICKET, json)).findFirst().orElseThrow();

        String consumeBody = "{\"protocolVersion\":\"1\",\"verificationTicket\":\"" + ticket
                + "\",\"action\":\"login\",\"contextDigest\":\"" + CONTEXT + "\"}";
        String consumePath = "/v1/trusted/sites/" + SITE_KEY + "/verification-tickets/consume";
        Map<String, String> trustedHeaders = Map.of("Authorization", "Bearer credential_1." + SECRET);
        CompletableFuture<HttpResponse<byte[]>> consumeFirst = postAsync(firstBase, consumePath, consumeBody, trustedHeaders);
        CompletableFuture<HttpResponse<byte[]>> consumeSecond = postAsync(secondBase, consumePath, consumeBody, trustedHeaders);
        assertEquals(List.of(200, 409), List.of(consumeFirst.join().statusCode(), consumeSecond.join().statusCode())
                .stream().sorted().toList());
        assertEquals(409, post(secondBase, consumePath, consumeBody, trustedHeaders).statusCode());

        assertEquals(200, post(secondBase, "/v1/public/sites/" + SITE_KEY + "/challenges",
                "{\"protocolVersion\":\"1\",\"action\":\"login\",\"contextDigest\":\"" + CONTEXT + "\"}",
                Map.of("Origin", ORIGIN)).statusCode());
        HttpResponse<byte[]> limited = post(firstBase, "/v1/public/sites/" + SITE_KEY + "/challenges",
                "{\"protocolVersion\":\"1\",\"action\":\"login\",\"contextDigest\":\"" + CONTEXT + "\"}",
                Map.of("Origin", ORIGIN));
        assertEquals(429, limited.statusCode());
        long retryAfter = Long.parseLong(limited.headers().firstValue("Retry-After").orElseThrow());
        assertTrue(retryAfter >= 1 && retryAfter <= 60);
        assertTrue(text(limited).contains("RATE_LIMITED"));
    }

    private static ConfigurableApplicationContext start(Map<String, Object> properties) {
        return new SpringApplicationBuilder(ChalSenseServerApplication.class).properties(properties)
                .run("--server.port=0");
    }

    private static Map<String, Object> properties() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Base64.getUrlDecoder().decode(SECRET));
        return Map.ofEntries(
                Map.entry("spring.main.banner-mode", "off"),
                Map.entry("logging.level.root", "WARN"),
                Map.entry("chalsense.redis-uri", redisUri),
                Map.entry("chalsense.redis-namespace", keyspace.namespace()),
                Map.entry("chalsense.background-directory", imageDirectory.toString()),
                Map.entry("chalsense.rate-limit.enabled", "true"),
                Map.entry("chalsense.rate-limit.hmac-key", SECRET),
                Map.entry("chalsense.sites[0].site-key", SITE_KEY),
                Map.entry("chalsense.sites[0].display-name", "HTTP integration test"),
                Map.entry("chalsense.sites[0].policy-version", "http-it-1"),
                Map.entry("chalsense.sites[0].allowed-actions[0]", "login"),
                Map.entry("chalsense.sites[0].allowed-origins[0]", ORIGIN),
                Map.entry("chalsense.sites[0].rate-limit.create-client.burst", "2"),
                Map.entry("chalsense.sites[0].rate-limit.create-client.interval", "60s"),
                Map.entry("chalsense.sites[0].credentials[0].key-id", "credential_1"),
                Map.entry("chalsense.sites[0].credentials[0].secret-sha256",
                        Base64.getUrlEncoder().withoutPadding().encodeToString(digest)));
    }

    private static URI base(ConfigurableApplicationContext context) {
        int port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
        return URI.create("http://127.0.0.1:" + port);
    }

    private static HttpResponse<byte[]> post(URI base, String path, String body, Map<String, String> headers)
            throws Exception {
        return postAsync(base, path, body, headers).get();
    }

    private static CompletableFuture<HttpResponse<byte[]>> postAsync(
            URI base, String path, String body, Map<String, String> headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(request::header);
        return http.sendAsync(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static HttpResponse<byte[]> get(URI base, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String text(HttpResponse<byte[]> response) {
        return new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String capture(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        assertTrue(matcher.find(), () -> "response does not match " + pattern + ": " + input);
        return matcher.group(1);
    }

    private static List<String> captures(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        List<String> values = new ArrayList<>();
        while (matcher.find()) values.add(matcher.group(1));
        return values;
    }

    private static void writeOwnedFixture(Path target) throws Exception {
        BufferedImage image = new BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(30, 70, 120));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(210, 180, 60));
            graphics.fillOval(80, 50, 420, 240);
        } finally {
            graphics.dispose();
        }
        assertTrue(ImageIO.write(image, "png", target.toFile()));
    }
}
