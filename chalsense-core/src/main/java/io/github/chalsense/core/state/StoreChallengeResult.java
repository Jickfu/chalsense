package io.github.chalsense.core.state;

/** Result of an atomic challenge create-if-absent operation. */
public enum StoreChallengeResult {
    CONFIRMED,
    ALREADY_EXISTS,
    FAILED,
    UNKNOWN
}
