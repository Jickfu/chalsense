package io.github.chalsense.server.http;

import io.github.chalsense.core.ticket.ConsumeOutcome;
import io.github.chalsense.core.ticket.ConsumeResult;
import io.github.chalsense.core.ticket.ConsumeTicketCommand;
import io.github.chalsense.core.ticket.TicketConsumer;
import io.github.chalsense.protocol.ActionName;
import io.github.chalsense.protocol.ContextDigest;
import io.github.chalsense.protocol.ProtocolVersion;
import io.github.chalsense.protocol.SiteKey;
import io.github.chalsense.protocol.VerificationTicket;
import io.github.chalsense.server.security.ServiceCredentialAuthenticator;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

@RestController
public final class TicketApiController {
    private final TicketConsumer consumer;
    private final ServiceCredentialAuthenticator authenticator;
    private final Clock clock;

    public TicketApiController(TicketConsumer consumer, ServiceCredentialAuthenticator authenticator, Clock clock) {
        this.consumer = consumer;
        this.authenticator = authenticator;
        this.clock = clock;
    }

    @PostMapping("/v1/trusted/sites/{siteKey}/verification-tickets/consume")
    ResponseEntity<?> consume(
            @PathVariable String siteKey,
            @RequestHeader(value = "Origin", required = false) String origin,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody ApiDtos.ConsumeRequest request) {
        SiteKey parsedSiteKey = new SiteKey(siteKey);
        if (origin != null || !authenticator.authenticate(parsedSiteKey, authorization, clock.millis())) {
            return ResponseEntity.status(401).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    .cacheControl(CacheControl.noStore())
                    .body(new ApiError("1", new ApiError.ErrorDetail(
                            "CALLER_UNAUTHORIZED", RequestIds.currentOrNext())));
        }
        if (!"1".equals(request.protocolVersion())) throw new IllegalArgumentException("unsupported protocolVersion");
        ConsumeResult result = consumer.consume(new ConsumeTicketCommand(
                ProtocolVersion.V1, new VerificationTicket(request.verificationTicket()), parsedSiteKey,
                new ActionName(request.action()), new ContextDigest(request.contextDigest())));
        if (result.outcome() == ConsumeOutcome.CONSUMED) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ApiDtos.ConsumedResponse(
                    "1", true, result.verifiedAt().orElseThrow(), result.consumedAt().orElseThrow()));
        }
        return switch (result.outcome()) {
            case TICKET_UNAVAILABLE -> ApiResponses.error(409, "TICKET_UNAVAILABLE");
            case TICKET_INVALID -> ApiResponses.error(422, "TICKET_INVALID");
            case CALLER_UNAUTHORIZED -> ApiResponses.error(403, "CALLER_UNAUTHORIZED");
            case DEPENDENCY_UNAVAILABLE -> ApiResponses.error(503, "DEPENDENCY_UNAVAILABLE");
            default -> throw new IllegalStateException("unmapped consume outcome");
        };
    }
}
