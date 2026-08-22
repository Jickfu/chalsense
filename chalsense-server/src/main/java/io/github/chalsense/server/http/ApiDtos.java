package io.github.chalsense.server.http;

import io.github.chalsense.core.challenge.ChallengeResource;
import io.github.chalsense.core.challenge.PublicSliderGeometry;

import java.util.List;

final class ApiDtos {
    private ApiDtos() {
    }

    record CreateRequest(String protocolVersion, String action, String contextDigest) {
    }

    record VerifyRequest(String protocolVersion, Solution solution) {
    }

    record Solution(long finalPieceX, List<TrackPointRequest> track) {
    }

    record TrackPointRequest(long x, long y, long t, String event) {
    }

    record ConsumeRequest(String protocolVersion, String verificationTicket, String action, String contextDigest) {
    }

    record CreatedResponse(
            String protocolVersion, String challengeId, String challengeType, long issuedAt, long expiresAt,
            PublicSliderGeometry geometry, List<ChallengeResource> resources) {
    }

    record VerifiedResponse(String protocolVersion, String verificationTicket, long issuedAt, long expiresAt) {
    }

    record ConsumedResponse(String protocolVersion, boolean valid, long verifiedAt, long consumedAt) {
    }
}
