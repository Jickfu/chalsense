package io.github.chalsense.server.http;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.RedisClient;

import java.util.Map;

@RestController
public final class HealthController {
    private final RedisClient redisClient;

    public HealthController(RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    @GetMapping("/livez")
    Map<String, String> live() {
        return Map.of("status", "UP");
    }

    @GetMapping("/readyz")
    ResponseEntity<Map<String, String>> ready() {
        try {
            return "PONG".equals(redisClient.ping())
                    ? ResponseEntity.ok(Map.of("status", "UP"))
                    : ResponseEntity.status(503).body(Map.of("status", "DOWN"));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
        }
    }
}
