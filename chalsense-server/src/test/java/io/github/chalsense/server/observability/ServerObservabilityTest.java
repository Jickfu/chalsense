package io.github.chalsense.server.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.chalsense.core.security.SecurityEvent;
import io.github.chalsense.core.security.SecurityOperation;
import io.github.chalsense.core.security.SecurityReason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

class ServerObservabilityTest {
    @Test
    void recordsOnlyBoundedTagsAndPrivacyMinimizedAuditFields() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        ServerObservability observability = new ServerObservability(meters);
        Logger logger = (Logger) LoggerFactory.getLogger("io.github.chalsense.audit");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        try {
            observability.record(new SecurityEvent(
                    SecurityOperation.CHALLENGE_VERIFY, SecurityReason.ANSWER_MISMATCH));
            observability.complete("AAAAAAAAAAAAAAAAAAAAAA", ObservedOperation.CHALLENGE_VERIFY,
                    "rejected", 422, 1_000_000);

            assertEquals(1, meters.get("chalsense.security.events")
                    .tags("operation", "challenge_verify", "reason", "answer_mismatch")
                    .counter().count());
            assertEquals(1, meters.get("chalsense.requests")
                    .tags("operation", "challenge_verify", "outcome", "rejected")
                    .counter().count());
            assertEquals(1, meters.get("chalsense.request.duration")
                    .tags("operation", "challenge_verify", "outcome", "rejected")
                    .timer().count());

            String message = appender.list.get(appender.list.size() - 1).getFormattedMessage();
            assertEquals("{\"event\":\"chalsense_request\",\"requestId\":\"AAAAAAAAAAAAAAAAAAAAAA\","
                    + "\"operation\":\"challenge_verify\",\"outcome\":\"rejected\","
                    + "\"reason\":\"answer_mismatch\",\"status\":422}", message);
            assertFalse(message.contains("siteKey"));
            assertFalse(message.contains("challengeId"));
            assertFalse(message.contains("ticket"));
            assertFalse(message.contains("contextDigest"));
            assertFalse(message.contains("track"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            meters.close();
        }
    }
}
