package cn.slainc.monitor.autoconfigure;

import cn.slainc.monitor.alert.AlertRule;
import cn.slainc.monitor.alert.WebhookDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "modality.monitor")
public class MonitorProperties {

    private boolean enabled = true;

    private long configCacheMillis = 5000L;

    private Metrics metrics = new Metrics();

    private SystemMetrics system = new SystemMetrics();

    private Http http = new Http();

    private Db db = new Db();

    private Redis redis = new Redis();

    private Jvm jvm = new Jvm();

    private Health health = new Health();

    private Info info = new Info();

    private SaToken saToken = new SaToken();

    private Alert alert = new Alert();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getConfigCacheMillis() {
        return configCacheMillis;
    }

    public void setConfigCacheMillis(long configCacheMillis) {
        this.configCacheMillis = configCacheMillis;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    public SystemMetrics getSystem() {
        return system;
    }

    public void setSystem(SystemMetrics system) {
        this.system = system;
    }

    public Http getHttp() {
        return http;
    }

    public void setHttp(Http http) {
        this.http = http;
    }

    public Db getDb() {
        return db;
    }

    public void setDb(Db db) {
        this.db = db;
    }

    public Redis getRedis() {
        return redis;
    }

    public void setRedis(Redis redis) {
        this.redis = redis;
    }

    public Jvm getJvm() {
        return jvm;
    }

    public void setJvm(Jvm jvm) {
        this.jvm = jvm;
    }

    public Health getHealth() {
        return health;
    }

    public void setHealth(Health health) {
        this.health = health;
    }

    public Info getInfo() {
        return info;
    }

    public void setInfo(Info info) {
        this.info = info;
    }

    public SaToken getSaToken() {
        return saToken;
    }

    public void setSaToken(SaToken saToken) {
        this.saToken = saToken;
    }

    public Alert getAlert() {
        return alert;
    }

    public void setAlert(Alert alert) {
        this.alert = alert;
    }

    public static class Metrics {

        private boolean enabled = true;

        private String prefix = "modality";

        private double samplingRate = 1.0d;

        private boolean histogram = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public double getSamplingRate() {
            return samplingRate;
        }

        public void setSamplingRate(double samplingRate) {
            this.samplingRate = samplingRate;
        }

        public boolean isHistogram() {
            return histogram;
        }

        public void setHistogram(boolean histogram) {
            this.histogram = histogram;
        }
    }

    public static class SystemMetrics {

        private boolean enabled = true;

        private long cacheMillis = 5000L;

        private boolean perCoreCpu = false;

        private List<String> diskMounts = new ArrayList<>();

        private List<String> networkInterfaces = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getCacheMillis() {
            return cacheMillis;
        }

        public void setCacheMillis(long cacheMillis) {
            this.cacheMillis = cacheMillis;
        }

        public boolean isPerCoreCpu() {
            return perCoreCpu;
        }

        public void setPerCoreCpu(boolean perCoreCpu) {
            this.perCoreCpu = perCoreCpu;
        }

        public List<String> getDiskMounts() {
            return diskMounts;
        }

        public void setDiskMounts(List<String> diskMounts) {
            this.diskMounts = diskMounts == null ? new ArrayList<>() : diskMounts;
        }

        public List<String> getNetworkInterfaces() {
            return networkInterfaces;
        }

        public void setNetworkInterfaces(List<String> networkInterfaces) {
            this.networkInterfaces = networkInterfaces == null ? new ArrayList<>() : networkInterfaces;
        }
    }

    public static class Http {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Db {

        private boolean enabled = true;

        private boolean druid = true;

        private long refreshMillis = 30000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isDruid() {
            return druid;
        }

        public void setDruid(boolean druid) {
            this.druid = druid;
        }

        public long getRefreshMillis() {
            return refreshMillis;
        }

        public void setRefreshMillis(long refreshMillis) {
            this.refreshMillis = refreshMillis;
        }
    }

    public static class Redis {

        private boolean enabled = true;

        private String instance = "default";

        private long cacheMillis = 15000L;

        private int slowlogCount = 20;

        private long slowlogThresholdMicros = 100000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getInstance() {
            return instance;
        }

        public void setInstance(String instance) {
            this.instance = instance;
        }

        public long getCacheMillis() {
            return cacheMillis;
        }

        public void setCacheMillis(long cacheMillis) {
            this.cacheMillis = cacheMillis;
        }

        public int getSlowlogCount() {
            return slowlogCount;
        }

        public void setSlowlogCount(int slowlogCount) {
            this.slowlogCount = slowlogCount;
        }

        public long getSlowlogThresholdMicros() {
            return slowlogThresholdMicros;
        }

        public void setSlowlogThresholdMicros(long slowlogThresholdMicros) {
            this.slowlogThresholdMicros = slowlogThresholdMicros;
        }
    }

    public static class Jvm {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Health {

        private boolean enabled = true;

        private boolean probesEnabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isProbesEnabled() {
            return probesEnabled;
        }

        public void setProbesEnabled(boolean probesEnabled) {
            this.probesEnabled = probesEnabled;
        }
    }

    public static class Info {

        private boolean enabled = true;

        private Map<String, String> business = new LinkedHashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Map<String, String> getBusiness() {
            return business;
        }

        public void setBusiness(Map<String, String> business) {
            this.business = business == null ? new LinkedHashMap<>() : business;
        }
    }

    public static class SaToken {

        private boolean enabled = true;

        private String instance = "default";

        private String tokenPrefix = "satoken:login:token:";

        private String sessionPrefix = "satoken:login:session:";

        private int scanLimit = 10000;

        private long cacheMillis = 60000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getInstance() {
            return instance;
        }

        public void setInstance(String instance) {
            this.instance = instance;
        }

        public String getTokenPrefix() {
            return tokenPrefix;
        }

        public void setTokenPrefix(String tokenPrefix) {
            this.tokenPrefix = tokenPrefix;
        }

        public String getSessionPrefix() {
            return sessionPrefix;
        }

        public void setSessionPrefix(String sessionPrefix) {
            this.sessionPrefix = sessionPrefix;
        }

        public int getScanLimit() {
            return scanLimit;
        }

        public void setScanLimit(int scanLimit) {
            this.scanLimit = scanLimit;
        }

        public long getCacheMillis() {
            return cacheMillis;
        }

        public void setCacheMillis(long cacheMillis) {
            this.cacheMillis = cacheMillis;
        }
    }

    public static class Alert {

        private boolean enabled = true;

        private long evaluationIntervalMillis = 15000L;

        private long suppressSeconds = 300L;

        private boolean defaultRules = true;

        private long webhookCacheMillis = 10000L;

        private Duration connectTimeout = Duration.ofSeconds(3);

        private Duration readTimeout = Duration.ofSeconds(5);

        private boolean allowInternalWebhooks = false;

        private List<AlertRule> rules = new ArrayList<>();

        private List<WebhookDefinition> webhooks = new ArrayList<>();

        private Silence silence = new Silence();

        private Notifiers notifiers = new Notifiers();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getEvaluationIntervalMillis() {
            return evaluationIntervalMillis;
        }

        public void setEvaluationIntervalMillis(long evaluationIntervalMillis) {
            this.evaluationIntervalMillis = evaluationIntervalMillis;
        }

        public long getSuppressSeconds() {
            return suppressSeconds;
        }

        public void setSuppressSeconds(long suppressSeconds) {
            this.suppressSeconds = suppressSeconds;
        }

        public boolean isDefaultRules() {
            return defaultRules;
        }

        public void setDefaultRules(boolean defaultRules) {
            this.defaultRules = defaultRules;
        }

        public long getWebhookCacheMillis() {
            return webhookCacheMillis;
        }

        public void setWebhookCacheMillis(long webhookCacheMillis) {
            this.webhookCacheMillis = webhookCacheMillis;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public boolean isAllowInternalWebhooks() {
            return allowInternalWebhooks;
        }

        public void setAllowInternalWebhooks(boolean allowInternalWebhooks) {
            this.allowInternalWebhooks = allowInternalWebhooks;
        }

        public List<AlertRule> getRules() {
            return rules;
        }

        public void setRules(List<AlertRule> rules) {
            this.rules = rules == null ? new ArrayList<>() : rules;
        }

        public List<WebhookDefinition> getWebhooks() {
            return webhooks;
        }

        public void setWebhooks(List<WebhookDefinition> webhooks) {
            this.webhooks = webhooks == null ? new ArrayList<>() : webhooks;
        }

        public Silence getSilence() {
            return silence;
        }

        public void setSilence(Silence silence) {
            this.silence = silence == null ? new Silence() : silence;
        }

        public Notifiers getNotifiers() {
            return notifiers;
        }

        public void setNotifiers(Notifiers notifiers) {
            this.notifiers = notifiers == null ? new Notifiers() : notifiers;
        }
    }

    public static class Silence {

        private String until;

        private List<String> rules = new ArrayList<>();

        public String getUntil() {
            return until;
        }

        public void setUntil(String until) {
            this.until = until;
        }

        public List<String> getRules() {
            return rules;
        }

        public void setRules(List<String> rules) {
            this.rules = rules == null ? new ArrayList<>() : rules;
        }
    }

    public static class Notifiers {

        private boolean log = true;

        private boolean webhook = true;

        public boolean isLog() {
            return log;
        }

        public void setLog(boolean log) {
            this.log = log;
        }

        public boolean isWebhook() {
            return webhook;
        }

        public void setWebhook(boolean webhook) {
            this.webhook = webhook;
        }
    }
}
