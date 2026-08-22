package io.github.chalsense.server.config;

import io.github.chalsense.core.site.SiteStatus;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("chalsense")
public final class ChalSenseServerProperties {
    private String redisUri = "redis://127.0.0.1:6379";
    private String redisNamespace = "chalsense";
    private String backgroundDirectory;
    private int maximumConcurrentGenerations = 4;
    private List<Site> sites = new ArrayList<>();
    private RateLimit rateLimit = new RateLimit();

    public String getRedisUri() { return redisUri; }
    public void setRedisUri(String redisUri) { this.redisUri = redisUri; }
    public String getRedisNamespace() { return redisNamespace; }
    public void setRedisNamespace(String redisNamespace) { this.redisNamespace = redisNamespace; }
    public String getBackgroundDirectory() { return backgroundDirectory; }
    public void setBackgroundDirectory(String backgroundDirectory) { this.backgroundDirectory = backgroundDirectory; }
    public int getMaximumConcurrentGenerations() { return maximumConcurrentGenerations; }
    public void setMaximumConcurrentGenerations(int value) { this.maximumConcurrentGenerations = value; }
    public List<Site> getSites() { return sites; }
    public void setSites(List<Site> sites) { this.sites = sites; }
    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit value) { rateLimit = value; }

    public static final class Site {
        private String siteKey;
        private String displayName;
        private SiteStatus status = SiteStatus.ACTIVE;
        private Duration challengeTtl = Duration.ofSeconds(120);
        private Duration ticketTtl = Duration.ofSeconds(60);
        private String policyVersion = "1";
        private boolean allowInsecureLoopbackOrigins;
        private List<String> allowedActions = new ArrayList<>();
        private List<String> allowedOrigins = new ArrayList<>();
        private List<Credential> credentials = new ArrayList<>();
        private SiteRateLimit rateLimit = new SiteRateLimit();

        public String getSiteKey() { return siteKey; }
        public void setSiteKey(String value) { siteKey = value; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String value) { displayName = value; }
        public SiteStatus getStatus() { return status; }
        public void setStatus(SiteStatus value) { status = value; }
        public Duration getChallengeTtl() { return challengeTtl; }
        public void setChallengeTtl(Duration value) { challengeTtl = value; }
        public Duration getTicketTtl() { return ticketTtl; }
        public void setTicketTtl(Duration value) { ticketTtl = value; }
        public String getPolicyVersion() { return policyVersion; }
        public void setPolicyVersion(String value) { policyVersion = value; }
        public boolean isAllowInsecureLoopbackOrigins() { return allowInsecureLoopbackOrigins; }
        public void setAllowInsecureLoopbackOrigins(boolean value) { allowInsecureLoopbackOrigins = value; }
        public List<String> getAllowedActions() { return allowedActions; }
        public void setAllowedActions(List<String> value) { allowedActions = value; }
        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> value) { allowedOrigins = value; }
        public List<Credential> getCredentials() { return credentials; }
        public void setCredentials(List<Credential> value) { credentials = value; }
        public SiteRateLimit getRateLimit() { return rateLimit; }
        public void setRateLimit(SiteRateLimit value) { rateLimit = value; }
    }

    public static final class Credential {
        private String keyId;
        private String secretSha256;
        private boolean active = true;
        private Long notBefore;
        private Long expiresAt;

        public String getKeyId() { return keyId; }
        public void setKeyId(String value) { keyId = value; }
        public String getSecretSha256() { return secretSha256; }
        public void setSecretSha256(String value) { secretSha256 = value; }
        public boolean isActive() { return active; }
        public void setActive(boolean value) { active = value; }
        public Long getNotBefore() { return notBefore; }
        public void setNotBefore(Long value) { notBefore = value; }
        public Long getExpiresAt() { return expiresAt; }
        public void setExpiresAt(Long value) { expiresAt = value; }
    }

    public static final class RateLimit {
        private boolean enabled;
        private String hmacKey;
        private List<String> trustedProxyCidrs = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public String getHmacKey() { return hmacKey; }
        public void setHmacKey(String value) { hmacKey = value; }
        public List<String> getTrustedProxyCidrs() { return trustedProxyCidrs; }
        public void setTrustedProxyCidrs(List<String> value) { trustedProxyCidrs = value; }
    }

    public static final class SiteRateLimit {
        private Limit createClient = new Limit(5, Duration.ofSeconds(12));
        private Limit createSite = new Limit(100, Duration.ofMillis(600));
        private Limit verifyClient = new Limit(10, Duration.ofSeconds(6));
        private Limit verifySite = new Limit(500, Duration.ofMillis(120));

        public Limit getCreateClient() { return createClient; }
        public void setCreateClient(Limit value) { createClient = value; }
        public Limit getCreateSite() { return createSite; }
        public void setCreateSite(Limit value) { createSite = value; }
        public Limit getVerifyClient() { return verifyClient; }
        public void setVerifyClient(Limit value) { verifyClient = value; }
        public Limit getVerifySite() { return verifySite; }
        public void setVerifySite(Limit value) { verifySite = value; }
    }

    public static final class Limit {
        private int burst;
        private Duration interval;

        public Limit() {}
        public Limit(int burst, Duration interval) { this.burst = burst; this.interval = interval; }
        public int getBurst() { return burst; }
        public void setBurst(int value) { burst = value; }
        public Duration getInterval() { return interval; }
        public void setInterval(Duration value) { interval = value; }
    }
}
