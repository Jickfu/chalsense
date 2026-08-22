package io.github.chalsense.core.challenge.slider;

import java.security.SecureRandom;
import java.util.Objects;

/** Injectable bounded random source. Production instances must wrap a CSPRNG. */
@FunctionalInterface
public interface BoundedRandom {
    int nextInt(int bound);

    static BoundedRandom secure(SecureRandom secureRandom) {
        Objects.requireNonNull(secureRandom, "secureRandom");
        return secureRandom::nextInt;
    }
}
