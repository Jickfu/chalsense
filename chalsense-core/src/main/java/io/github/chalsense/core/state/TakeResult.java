package io.github.chalsense.core.state;

import java.util.Objects;

/** Result of one atomic read-and-delete operation; implementations must never restore taken state. */
public sealed interface TakeResult<T>
        permits TakeResult.Present, TakeResult.Absent, TakeResult.Failed, TakeResult.Unknown,
        TakeResult.Unreadable {
    record Present<T>(T state) implements TakeResult<T> {
        public Present {
            Objects.requireNonNull(state, "state");
        }
    }

    record Absent<T>() implements TakeResult<T> {
    }

    /** The store confirmed that the operation did not take state. */
    record Failed<T>() implements TakeResult<T> {
    }

    /** The caller cannot know whether state was taken and must abandon the credential. */
    record Unknown<T>() implements TakeResult<T> {
    }

    /** Raw state was atomically taken and not restored, but the frozen reader could not decode it. */
    record Unreadable<T>() implements TakeResult<T> {
    }
}
