package io.github.chalsense.core.security;

/** Low-cardinality internal reasons that must never be copied into public error responses. */
public enum SecurityReason {
    GENERATOR_FAILED,
    CHALLENGE_ID_COLLISION,
    EXPIRED,
    CALLER_UNAUTHORIZED,
    ORIGIN_NOT_ALLOWED,
    ACTION_NOT_ALLOWED,
    PROTOCOL_MISMATCH,
    SITE_MISMATCH,
    ACTION_MISMATCH,
    CONTEXT_MISMATCH,
    TRACK_STRUCTURE,
    FINAL_POSITION_MISMATCH,
    ANSWER_MISMATCH,
    STORE_FAILED,
    STORE_RESULT_UNKNOWN,
    STORE_STATE_UNREADABLE,
    TICKET_WRITE_FAILED,
    TICKET_WRITE_RESULT_UNKNOWN
}
