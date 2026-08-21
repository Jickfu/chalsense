package io.github.chalsense.core.site;

import java.util.Objects;

/** Trusted process context supplied by an adapter; this type is never parsed from request JSON. */
public sealed interface CallerContext permits CallerContext.TrustedBackend, CallerContext.PublicBrowser {
    static CallerContext trustedBackend() {
        return new TrustedBackend();
    }

    static CallerContext publicBrowser(WebOrigin origin) {
        return new PublicBrowser(origin);
    }

    record TrustedBackend() implements CallerContext {
    }

    record PublicBrowser(WebOrigin origin) implements CallerContext {
        public PublicBrowser {
            Objects.requireNonNull(origin, "origin");
        }
    }
}

