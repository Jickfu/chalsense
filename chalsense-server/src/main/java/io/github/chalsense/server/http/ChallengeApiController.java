package io.github.chalsense.server.http;

import io.github.chalsense.core.challenge.ChallengeCreator;
import io.github.chalsense.core.challenge.CreateChallengeCommand;
import io.github.chalsense.core.challenge.CreateOutcome;
import io.github.chalsense.core.challenge.CreateResult;
import io.github.chalsense.core.challenge.CreatedChallenge;
import io.github.chalsense.core.site.CallerContext;
import io.github.chalsense.core.site.WebOrigin;
import io.github.chalsense.core.verify.ChallengeVerifier;
import io.github.chalsense.core.verify.VerifyChallengeCommand;
import io.github.chalsense.core.verify.VerifyOutcome;
import io.github.chalsense.core.verify.VerifyResult;
import io.github.chalsense.protocol.ActionName;
import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.ContextDigest;
import io.github.chalsense.protocol.ProtocolVersion;
import io.github.chalsense.protocol.SiteKey;
import io.github.chalsense.protocol.Track;
import io.github.chalsense.protocol.TrackEvent;
import io.github.chalsense.protocol.TrackPoint;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class ChallengeApiController {
    private final ChallengeCreator creator;
    private final ChallengeVerifier verifier;

    public ChallengeApiController(ChallengeCreator creator, ChallengeVerifier verifier) {
        this.creator = creator;
        this.verifier = verifier;
    }

    @PostMapping("/v1/public/sites/{siteKey}/challenges")
    ResponseEntity<?> create(
            @PathVariable String siteKey,
            @RequestHeader("Origin") String origin,
            @RequestBody ApiDtos.CreateRequest request) {
        CreateResult result = creator.create(new CreateChallengeCommand(
                protocol(request.protocolVersion()), new SiteKey(siteKey), new ActionName(request.action()),
                new ContextDigest(request.contextDigest()), CallerContext.publicBrowser(WebOrigin.parse(origin))));
        if (result.outcome() == CreateOutcome.CHALLENGE_CREATED) {
            CreatedChallenge challenge = result.challenge().orElseThrow();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ApiDtos.CreatedResponse(
                    "1", challenge.challengeId().value(), challenge.challengeType().name(), challenge.issuedAt(),
                    challenge.expiresAt(), challenge.geometry(), challenge.resources()));
        }
        return switch (result.outcome()) {
            case CALLER_UNAUTHORIZED -> ApiResponses.error(403, "CALLER_UNAUTHORIZED");
            case ORIGIN_NOT_ALLOWED -> ApiResponses.error(403, "ORIGIN_NOT_ALLOWED");
            case DEPENDENCY_UNAVAILABLE -> ApiResponses.error(503, "DEPENDENCY_UNAVAILABLE");
            default -> throw new IllegalStateException("unmapped create outcome");
        };
    }

    @PostMapping("/v1/public/sites/{siteKey}/challenges/{challengeId}/verify")
    ResponseEntity<?> verify(
            @PathVariable String siteKey,
            @PathVariable String challengeId,
            @RequestHeader("Origin") String origin,
            @RequestBody ApiDtos.VerifyRequest request) {
        if (request.solution() == null || request.solution().track() == null) {
            throw new IllegalArgumentException("solution is required");
        }
        Track track = new Track(request.solution().track().stream().map(point -> new TrackPoint(
                point.x(), point.y(), point.t(), TrackEvent.valueOf(point.event()))).toList());
        VerifyResult result = verifier.verify(new VerifyChallengeCommand(
                protocol(request.protocolVersion()), new SiteKey(siteKey), new ChallengeId(challengeId),
                request.solution().finalPieceX(), track, CallerContext.publicBrowser(WebOrigin.parse(origin))));
        if (result.outcome() == VerifyOutcome.TICKET_ISSUED) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ApiDtos.VerifiedResponse(
                    "1", result.verificationTicket().orElseThrow().value(), result.issuedAt().orElseThrow(),
                    result.expiresAt().orElseThrow()));
        }
        return switch (result.outcome()) {
            case VERIFICATION_FAILED -> ApiResponses.error(422, "VERIFICATION_FAILED");
            case CHALLENGE_UNAVAILABLE -> ApiResponses.error(409, "CHALLENGE_UNAVAILABLE");
            case CALLER_UNAUTHORIZED -> ApiResponses.error(403, "CALLER_UNAUTHORIZED");
            case ORIGIN_NOT_ALLOWED -> ApiResponses.error(403, "ORIGIN_NOT_ALLOWED");
            case DEPENDENCY_UNAVAILABLE -> ApiResponses.error(503, "DEPENDENCY_UNAVAILABLE");
            default -> throw new IllegalStateException("unmapped verify outcome");
        };
    }

    private static ProtocolVersion protocol(String value) {
        if (!"1".equals(value)) throw new IllegalArgumentException("unsupported protocolVersion");
        return ProtocolVersion.V1;
    }
}
