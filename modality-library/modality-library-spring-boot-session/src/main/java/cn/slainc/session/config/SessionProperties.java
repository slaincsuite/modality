package cn.slainc.session.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "modality.session")
public class SessionProperties {

    private boolean enabled = true;

    private long ttlSeconds = 0;

    private long idleTimeoutSeconds = 0;

    private long maxLifetimeSeconds = 2592000;

    private int maxScan = 10000;

    private int maxSessionsPerUser = 0;

    private final Display display = new Display();

    private final Query query = new Query();

    private final Online online = new Online();

    private final Stats stats = new Stats();

    private final Kick kick = new Kick();

    private final Extend extend = new Extend();

    private final Renew renew = new Renew();

    private final Limit limit = new Limit();

    private final Notify notify = new Notify();

    private final Metrics metrics = new Metrics();

    private final Cleanup cleanup = new Cleanup();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public long getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    public void setIdleTimeoutSeconds(long idleTimeoutSeconds) {
        this.idleTimeoutSeconds = idleTimeoutSeconds;
    }

    public long getMaxLifetimeSeconds() {
        return maxLifetimeSeconds;
    }

    public void setMaxLifetimeSeconds(long maxLifetimeSeconds) {
        this.maxLifetimeSeconds = maxLifetimeSeconds;
    }

    public int getMaxScan() {
        return maxScan;
    }

    public void setMaxScan(int maxScan) {
        this.maxScan = maxScan;
    }

    public int getMaxSessionsPerUser() {
        return maxSessionsPerUser;
    }

    public void setMaxSessionsPerUser(int maxSessionsPerUser) {
        this.maxSessionsPerUser = maxSessionsPerUser;
    }

    public Display getDisplay() {
        return display;
    }

    public Query getQuery() {
        return query;
    }

    public Online getOnline() {
        return online;
    }

    public Stats getStats() {
        return stats;
    }

    public Kick getKick() {
        return kick;
    }

    public Extend getExtend() {
        return extend;
    }

    public Renew getRenew() {
        return renew;
    }

    public Limit getLimit() {
        return limit;
    }

    public Notify getNotify() {
        return notify;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public Cleanup getCleanup() {
        return cleanup;
    }

    public static class Display {

        private boolean maskToken = true;

        public boolean isMaskToken() {
            return maskToken;
        }

        public void setMaskToken(boolean maskToken) {
            this.maskToken = maskToken;
        }
    }

    public static class Query {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Online {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Stats {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Kick {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Extend {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Renew {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Limit {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Notify {

        private boolean enabled = true;

        private boolean websocketEnabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isWebsocketEnabled() {
            return websocketEnabled;
        }

        public void setWebsocketEnabled(boolean websocketEnabled) {
            this.websocketEnabled = websocketEnabled;
        }
    }

    public static class Metrics {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Cleanup {

        private boolean enabled = false;

        private int intervalMinutes = 30;

        private long zombieIdleSeconds = 0;

        private boolean absoluteTimeoutEnabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getIntervalMinutes() {
            return intervalMinutes;
        }

        public void setIntervalMinutes(int intervalMinutes) {
            this.intervalMinutes = intervalMinutes;
        }

        public long getZombieIdleSeconds() {
            return zombieIdleSeconds;
        }

        public void setZombieIdleSeconds(long zombieIdleSeconds) {
            this.zombieIdleSeconds = zombieIdleSeconds;
        }

        public boolean isAbsoluteTimeoutEnabled() {
            return absoluteTimeoutEnabled;
        }

        public void setAbsoluteTimeoutEnabled(boolean absoluteTimeoutEnabled) {
            this.absoluteTimeoutEnabled = absoluteTimeoutEnabled;
        }
    }
}
