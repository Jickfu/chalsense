package io.github.chalsense.core.site;

import io.github.chalsense.protocol.ActionName;

public final class SiteAuthorizer {
    private SiteAuthorizer() {
    }

    public static SiteAuthorization authorizeCaller(SiteRegistration registration, CallerContext callerContext) {
        if (registration.status() != SiteStatus.ACTIVE) {
            return SiteAuthorization.CALLER_UNAUTHORIZED;
        }
        if (callerContext instanceof CallerContext.TrustedBackend) {
            return SiteAuthorization.ALLOWED;
        }
        WebOrigin origin = ((CallerContext.PublicBrowser) callerContext).origin();
        return registration.policy().allowedOrigins().contains(origin)
                ? SiteAuthorization.ALLOWED
                : SiteAuthorization.ORIGIN_NOT_ALLOWED;
    }

    public static boolean allowsAction(SiteRegistration registration, ActionName action) {
        return registration.status() == SiteStatus.ACTIVE
                && registration.policy().allowedActions().contains(action);
    }
}
