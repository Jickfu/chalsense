package io.github.chalsense.server.http;

import io.github.chalsense.core.challenge.ChallengeResourceRole;
import io.github.chalsense.core.challenge.resource.ChallengeResourceContent;
import io.github.chalsense.core.challenge.resource.ChallengeResourceReadResult;
import io.github.chalsense.core.challenge.resource.ChallengeResourceReader;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

@RestController
public final class ResourceApiController {
    private final ChallengeResourceReader reader;
    private final Clock clock;

    public ResourceApiController(ChallengeResourceReader reader, Clock clock) {
        this.reader = reader;
        this.clock = clock;
    }

    @RequestMapping(path = "/v1/public/resources/{resourceId}/{role}", method = { RequestMethod.GET, RequestMethod.HEAD })
    ResponseEntity<byte[]> resource(@PathVariable String resourceId, @PathVariable String role) {
        final ChallengeResourceRole parsedRole;
        try {
            parsedRole = ChallengeResourceRole.valueOf(role.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        }
        long now = clock.millis();
        ChallengeResourceReadResult result = reader.read(resourceId, parsedRole, now);
        if (!(result instanceof ChallengeResourceReadResult.Found found)) {
            return ResponseEntity.notFound().build();
        }
        ChallengeResourceContent content = found.content();
        byte[] bytes = content.bytes();
        long seconds = Math.max(0, (content.expiresAt() - now) / 1000);
        return ResponseEntity.ok()
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer")
                .header("Cross-Origin-Resource-Policy", "cross-origin")
                .header(HttpHeaders.CONTENT_TYPE, content.mediaType())
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(seconds)).cachePrivate())
                .contentLength(bytes.length)
                .body(bytes);
    }
}
