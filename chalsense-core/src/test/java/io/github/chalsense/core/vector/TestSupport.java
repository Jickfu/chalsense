package io.github.chalsense.core.vector;

import io.github.chalsense.core.TokenGenerator;
import io.github.chalsense.core.security.SecurityEvent;
import io.github.chalsense.core.security.SecurityEventSink;
import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.VerificationTicket;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class TestSupport {
    private TestSupport() {
    }

    static final class FixedTokenGenerator implements TokenGenerator {
        private final ChallengeId challengeId;
        private final VerificationTicket verificationTicket;

        FixedTokenGenerator(ChallengeId challengeId, VerificationTicket verificationTicket) {
            this.challengeId = challengeId;
            this.verificationTicket = verificationTicket;
        }

        @Override
        public ChallengeId newChallengeId() {
            return challengeId;
        }

        @Override
        public VerificationTicket newVerificationTicket() {
            return verificationTicket;
        }
    }

    static final class CollectingSecurityEventSink implements SecurityEventSink {
        private final List<SecurityEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void record(SecurityEvent event) {
            events.add(event);
        }

        List<SecurityEvent> events() {
            return List.copyOf(events);
        }
    }
}
