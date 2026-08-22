package io.github.chalsense.server.observability;

import io.github.chalsense.core.security.SecurityEvent;
import io.github.chalsense.core.security.SecurityEventSink;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Records only bounded enums and a server-generated request id. */
@Component
public final class ServerObservability implements SecurityEventSink {
    private static final Logger AUDIT = LoggerFactory.getLogger("io.github.chalsense.audit");
    private static final String NO_REASON = "none";

    private final MeterRegistry meters;
    private final ThreadLocal<String> internalReason = new ThreadLocal<>();

    public ServerObservability(MeterRegistry meters) {
        this.meters = meters;
    }

    @Override
    public void record(SecurityEvent event) {
        String operation = lower(event.operation().name());
        String reason = lower(event.reason().name());
        internalReason.set(reason);
        meters.counter("chalsense.security.events", "operation", operation, "reason", reason).increment();
    }

    public void complete(String requestId, ObservedOperation operation, String outcome, int status, long elapsedNanos) {
        String reason = internalReason.get();
        if (reason == null) reason = NO_REASON;
        try {
            String operationName = lower(operation.name());
            meters.counter("chalsense.requests", "operation", operationName, "outcome", outcome).increment();
            Timer.builder("chalsense.request.duration")
                    .tags("operation", operationName, "outcome", outcome)
                    .register(meters).record(elapsedNanos, TimeUnit.NANOSECONDS);
            AUDIT.info("{\"event\":\"chalsense_request\",\"requestId\":\"{}\","
                            + "\"operation\":\"{}\",\"outcome\":\"{}\",\"reason\":\"{}\",\"status\":{}}",
                    requestId, operationName, outcome, reason, status);
        } catch (RuntimeException ignored) {
            // Observability is deliberately non-blocking and cannot change protocol outcomes.
        } finally {
            internalReason.remove();
        }
    }

    public void clear() {
        internalReason.remove();
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
