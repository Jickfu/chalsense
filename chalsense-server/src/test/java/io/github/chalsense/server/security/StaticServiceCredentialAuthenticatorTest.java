package io.github.chalsense.server.security;

import io.github.chalsense.protocol.SiteKey;
import io.github.chalsense.server.config.ChalSenseServerProperties;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticServiceCredentialAuthenticatorTest {
    @Test
    void bindsCredentialToSiteStatusWindowAndDigest() throws Exception {
        byte[] secretBytes = new byte[32];
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        ChalSenseServerProperties.Credential credential = new ChalSenseServerProperties.Credential();
        credential.setKeyId("credential_1");
        credential.setSecretSha256(Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(secretBytes)));
        credential.setNotBefore(1000L);
        credential.setExpiresAt(2000L);
        ChalSenseServerProperties.Site site = new ChalSenseServerProperties.Site();
        site.setSiteKey("site_test");
        site.setCredentials(List.of(credential));
        ChalSenseServerProperties properties = new ChalSenseServerProperties();
        properties.setSites(List.of(site));
        StaticServiceCredentialAuthenticator authenticator = new StaticServiceCredentialAuthenticator(properties);

        assertTrue(authenticator.authenticate(new SiteKey("site_test"), "Bearer credential_1." + secret, 1500));
        assertFalse(authenticator.authenticate(new SiteKey("site_test"), "Bearer credential_1." + secret, 999));
        assertFalse(authenticator.authenticate(new SiteKey("site_test"), "Bearer credential_1." + secret, 2000));
        assertFalse(authenticator.authenticate(new SiteKey("other_site"), "Bearer credential_1." + secret, 1500));
        assertFalse(authenticator.authenticate(new SiteKey("site_test"), "Bearer unknown_id1." + secret, 1500));
    }
}
