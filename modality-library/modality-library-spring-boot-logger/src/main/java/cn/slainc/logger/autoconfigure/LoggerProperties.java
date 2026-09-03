package cn.slainc.logger.autoconfigure;

import cn.slainc.logger.mask.MaskRule;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "modality.logger")
public class LoggerProperties {

    private boolean enabled = true;

    private final Mask mask = new Mask();

    private final Json json = new Json();

    private final Async async = new Async();

    private final Output output = new Output();

    private final Context context = new Context();

    private final Trace trace = new Trace();

    private final Sample sample = new Sample();

    private final Level level = new Level();

    private final Audit audit = new Audit();

    private final Archive archive = new Archive();

    private final Query query = new Query();

    private final Monitor monitor = new Monitor();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Mask getMask() {
        return mask;
    }

    public Json getJson() {
        return json;
    }

    public Async getAsync() {
        return async;
    }

    public Output getOutput() {
        return output;
    }

    public Context getContext() {
        return context;
    }

    public Trace getTrace() {
        return trace;
    }

    public Sample getSample() {
        return sample;
    }

    public Level getLevel() {
        return level;
    }

    public Audit getAudit() {
        return audit;
    }

    public Archive getArchive() {
        return archive;
    }

    public Query getQuery() {
        return query;
    }

    public Monitor getMonitor() {
        return monitor;
    }

    public static class Mask {

        private boolean enabled = true;

        private String replacement = "******";

        private boolean maskJsonMessage = true;

        private boolean maskTextMessage = true;

        private List<String> keywords = new ArrayList<>(List.of(
                "password", "passwd", "pwd", "token", "secret", "credential", "privatekey", "accesskey",
                "密码", "手机号", "手机", "电话", "身份证", "银行卡", "邮箱", "令牌", "密钥"));

        private List<String> whitelist = new ArrayList<>();

        private List<MaskRule> rules = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getReplacement() {
            return replacement;
        }

        public void setReplacement(String replacement) {
            this.replacement = replacement;
        }

        public boolean isMaskJsonMessage() {
            return maskJsonMessage;
        }

        public void setMaskJsonMessage(boolean maskJsonMessage) {
            this.maskJsonMessage = maskJsonMessage;
        }

        public boolean isMaskTextMessage() {
            return maskTextMessage;
        }

        public void setMaskTextMessage(boolean maskTextMessage) {
            this.maskTextMessage = maskTextMessage;
        }

        public List<String> getKeywords() {
            return keywords;
        }

        public void setKeywords(List<String> keywords) {
            this.keywords = keywords;
        }

        public List<String> getWhitelist() {
            return whitelist;
        }

        public void setWhitelist(List<String> whitelist) {
            this.whitelist = whitelist;
        }

        public List<MaskRule> getRules() {
            return rules;
        }

        public void setRules(List<MaskRule> rules) {
            this.rules = rules;
        }
    }

    public static class Json {

        private boolean enabled = true;

        private boolean attachToRoot = true;

        private boolean replaceRootAppenders = false;

        private boolean includeMdcFields = true;

        private boolean includeThread = true;

        private boolean includeException = true;

        private int maxMessageLength = 8192;

        private int maxStackDepth = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAttachToRoot() {
            return attachToRoot;
        }

        public void setAttachToRoot(boolean attachToRoot) {
            this.attachToRoot = attachToRoot;
        }

        public boolean isReplaceRootAppenders() {
            return replaceRootAppenders;
        }

        public void setReplaceRootAppenders(boolean replaceRootAppenders) {
            this.replaceRootAppenders = replaceRootAppenders;
        }

        public boolean isIncludeMdcFields() {
            return includeMdcFields;
        }

        public void setIncludeMdcFields(boolean includeMdcFields) {
            this.includeMdcFields = includeMdcFields;
        }

        public boolean isIncludeThread() {
            return includeThread;
        }

        public void setIncludeThread(boolean includeThread) {
            this.includeThread = includeThread;
        }

        public boolean isIncludeException() {
            return includeException;
        }

        public void setIncludeException(boolean includeException) {
            this.includeException = includeException;
        }

        public int getMaxMessageLength() {
            return maxMessageLength;
        }

        public void setMaxMessageLength(int maxMessageLength) {
            this.maxMessageLength = maxMessageLength;
        }

        public int getMaxStackDepth() {
            return maxStackDepth;
        }

        public void setMaxStackDepth(int maxStackDepth) {
            this.maxStackDepth = maxStackDepth;
        }
    }

    public static class Async {

        private boolean enabled = true;

        private int queueSize = 8192;

        private int discardingThreshold = 0;

        private boolean neverBlock = true;

        private int maxFlushTimeMillis = 1000;

        private boolean includeCallerData = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getQueueSize() {
            return queueSize;
        }

        public void setQueueSize(int queueSize) {
            this.queueSize = queueSize;
        }

        public int getDiscardingThreshold() {
            return discardingThreshold;
        }

        public void setDiscardingThreshold(int discardingThreshold) {
            this.discardingThreshold = discardingThreshold;
        }

        public boolean isNeverBlock() {
            return neverBlock;
        }

        public void setNeverBlock(boolean neverBlock) {
            this.neverBlock = neverBlock;
        }

        public int getMaxFlushTimeMillis() {
            return maxFlushTimeMillis;
        }

        public void setMaxFlushTimeMillis(int maxFlushTimeMillis) {
            this.maxFlushTimeMillis = maxFlushTimeMillis;
        }

        public boolean isIncludeCallerData() {
            return includeCallerData;
        }

        public void setIncludeCallerData(boolean includeCallerData) {
            this.includeCallerData = includeCallerData;
        }
    }

    public static class Output {

        private boolean enabled = true;

        private String dir = "logs";

        private String fileName = "modality.log";

        private String rollingPattern = "%d{yyyy-MM-dd}.%i.log.gz";

        private String maxFileSize = "100MB";

        private int maxHistoryDays = 30;

        private String totalSizeCap = "10GB";

        private boolean immediateFlush = false;

        private boolean append = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getRollingPattern() {
            return rollingPattern;
        }

        public void setRollingPattern(String rollingPattern) {
            this.rollingPattern = rollingPattern;
        }

        public String getMaxFileSize() {
            return maxFileSize;
        }

        public void setMaxFileSize(String maxFileSize) {
            this.maxFileSize = maxFileSize;
        }

        public int getMaxHistoryDays() {
            return maxHistoryDays;
        }

        public void setMaxHistoryDays(int maxHistoryDays) {
            this.maxHistoryDays = maxHistoryDays;
        }

        public String getTotalSizeCap() {
            return totalSizeCap;
        }

        public void setTotalSizeCap(String totalSizeCap) {
            this.totalSizeCap = totalSizeCap;
        }

        public boolean isImmediateFlush() {
            return immediateFlush;
        }

        public void setImmediateFlush(boolean immediateFlush) {
            this.immediateFlush = immediateFlush;
        }

        public boolean isAppend() {
            return append;
        }

        public void setAppend(boolean append) {
            this.append = append;
        }
    }

    public static class Context {

        private boolean enabled = true;

        private int maxUserAgentLength = 256;

        private boolean injectTenant = true;

        private boolean injectUser = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxUserAgentLength() {
            return maxUserAgentLength;
        }

        public void setMaxUserAgentLength(int maxUserAgentLength) {
            this.maxUserAgentLength = maxUserAgentLength;
        }

        public boolean isInjectTenant() {
            return injectTenant;
        }

        public void setInjectTenant(boolean injectTenant) {
            this.injectTenant = injectTenant;
        }

        public boolean isInjectUser() {
            return injectUser;
        }

        public void setInjectUser(boolean injectUser) {
            this.injectUser = injectUser;
        }
    }

    public static class Trace {

        private boolean enabled = true;

        private boolean spanEnabled = true;

        private String spanIdStyle = "uuid";

        private boolean propagate = true;

        private String spanHeaderName = "X-Span-Id";

        private String parentSpanHeaderName = "X-Parent-Span-Id";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isSpanEnabled() {
            return spanEnabled;
        }

        public void setSpanEnabled(boolean spanEnabled) {
            this.spanEnabled = spanEnabled;
        }

        public String getSpanIdStyle() {
            return spanIdStyle;
        }

        public void setSpanIdStyle(String spanIdStyle) {
            this.spanIdStyle = spanIdStyle;
        }

        public boolean isPropagate() {
            return propagate;
        }

        public void setPropagate(boolean propagate) {
            this.propagate = propagate;
        }

        public String getSpanHeaderName() {
            return spanHeaderName;
        }

        public void setSpanHeaderName(String spanHeaderName) {
            this.spanHeaderName = spanHeaderName;
        }

        public String getParentSpanHeaderName() {
            return parentSpanHeaderName;
        }

        public void setParentSpanHeaderName(String parentSpanHeaderName) {
            this.parentSpanHeaderName = parentSpanHeaderName;
        }
    }

    public static class Sample {

        private boolean enabled = false;

        private String strategy = "FIXED";

        private double rate = 1.0d;

        private double targetPerSecond = 1000.0d;

        private double minRate = 0.01d;

        private long windowMillis = 1000L;

        private String alwaysLogLevel = "WARN";

        private boolean exemptCriticalMarker = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public double getRate() {
            return rate;
        }

        public void setRate(double rate) {
            this.rate = rate;
        }

        public double getTargetPerSecond() {
            return targetPerSecond;
        }

        public void setTargetPerSecond(double targetPerSecond) {
            this.targetPerSecond = targetPerSecond;
        }

        public double getMinRate() {
            return minRate;
        }

        public void setMinRate(double minRate) {
            this.minRate = minRate;
        }

        public long getWindowMillis() {
            return windowMillis;
        }

        public void setWindowMillis(long windowMillis) {
            this.windowMillis = windowMillis;
        }

        public String getAlwaysLogLevel() {
            return alwaysLogLevel;
        }

        public void setAlwaysLogLevel(String alwaysLogLevel) {
            this.alwaysLogLevel = alwaysLogLevel;
        }

        public boolean isExemptCriticalMarker() {
            return exemptCriticalMarker;
        }

        public void setExemptCriticalMarker(boolean exemptCriticalMarker) {
            this.exemptCriticalMarker = exemptCriticalMarker;
        }
    }

    public static class Level {

        private boolean enabled = true;

        private String configGroup = "logger-level";

        private String tenantConfigGroup = "logger-tenant-level";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getConfigGroup() {
            return configGroup;
        }

        public void setConfigGroup(String configGroup) {
            this.configGroup = configGroup;
        }

        public String getTenantConfigGroup() {
            return tenantConfigGroup;
        }

        public void setTenantConfigGroup(String tenantConfigGroup) {
            this.tenantConfigGroup = tenantConfigGroup;
        }
    }

    public static class Audit {

        private boolean enabled = true;

        private boolean async = true;

        private int queueCapacity = 1024;

        private int corePoolSize = 1;

        private int maxPoolSize = 4;

        private int maxPayloadLength = 1024;

        private boolean recordBeforeSnapshot = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAsync() {
            return async;
        }

        public void setAsync(boolean async) {
            this.async = async;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getMaxPayloadLength() {
            return maxPayloadLength;
        }

        public void setMaxPayloadLength(int maxPayloadLength) {
            this.maxPayloadLength = maxPayloadLength;
        }

        public boolean isRecordBeforeSnapshot() {
            return recordBeforeSnapshot;
        }

        public void setRecordBeforeSnapshot(boolean recordBeforeSnapshot) {
            this.recordBeforeSnapshot = recordBeforeSnapshot;
        }
    }

    public static class Archive {

        private boolean enabled = true;

        private String archiveDir = "";

        private int retentionDays = 30;

        private int archiveRetentionDays = 365;

        private int coldThresholdDays = 7;

        private String cycle = "DAY";

        private boolean gzip = true;

        private boolean deleteAfterArchive = true;

        private long intervalMinutes = 60L;

        private boolean runOnStartup = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getArchiveDir() {
            return archiveDir;
        }

        public void setArchiveDir(String archiveDir) {
            this.archiveDir = archiveDir;
        }

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }

        public int getArchiveRetentionDays() {
            return archiveRetentionDays;
        }

        public void setArchiveRetentionDays(int archiveRetentionDays) {
            this.archiveRetentionDays = archiveRetentionDays;
        }

        public int getColdThresholdDays() {
            return coldThresholdDays;
        }

        public void setColdThresholdDays(int coldThresholdDays) {
            this.coldThresholdDays = coldThresholdDays;
        }

        public String getCycle() {
            return cycle;
        }

        public void setCycle(String cycle) {
            this.cycle = cycle;
        }

        public boolean isGzip() {
            return gzip;
        }

        public void setGzip(boolean gzip) {
            this.gzip = gzip;
        }

        public boolean isDeleteAfterArchive() {
            return deleteAfterArchive;
        }

        public void setDeleteAfterArchive(boolean deleteAfterArchive) {
            this.deleteAfterArchive = deleteAfterArchive;
        }

        public long getIntervalMinutes() {
            return intervalMinutes;
        }

        public void setIntervalMinutes(long intervalMinutes) {
            this.intervalMinutes = intervalMinutes;
        }

        public boolean isRunOnStartup() {
            return runOnStartup;
        }

        public void setRunOnStartup(boolean runOnStartup) {
            this.runOnStartup = runOnStartup;
        }
    }

    public static class Query {

        private boolean enabled = true;

        private int maxFiles = 200;

        private long maxLinesPerFile = 200000L;

        private int maxResults = 10000;

        private boolean includeArchived = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxFiles() {
            return maxFiles;
        }

        public void setMaxFiles(int maxFiles) {
            this.maxFiles = maxFiles;
        }

        public long getMaxLinesPerFile() {
            return maxLinesPerFile;
        }

        public void setMaxLinesPerFile(long maxLinesPerFile) {
            this.maxLinesPerFile = maxLinesPerFile;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }

        public boolean isIncludeArchived() {
            return includeArchived;
        }

        public void setIncludeArchived(boolean includeArchived) {
            this.includeArchived = includeArchived;
        }
    }

    public static class Monitor {

        private boolean enabled = true;

        private double errorRateThreshold = 0.05d;

        private long minSampleCount = 100L;

        private long errorCountThreshold = 50L;

        private long windowSeconds = 60L;

        private long alertIntervalSeconds = 60L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getErrorRateThreshold() {
            return errorRateThreshold;
        }

        public void setErrorRateThreshold(double errorRateThreshold) {
            this.errorRateThreshold = errorRateThreshold;
        }

        public long getMinSampleCount() {
            return minSampleCount;
        }

        public void setMinSampleCount(long minSampleCount) {
            this.minSampleCount = minSampleCount;
        }

        public long getErrorCountThreshold() {
            return errorCountThreshold;
        }

        public void setErrorCountThreshold(long errorCountThreshold) {
            this.errorCountThreshold = errorCountThreshold;
        }

        public long getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public long getAlertIntervalSeconds() {
            return alertIntervalSeconds;
        }

        public void setAlertIntervalSeconds(long alertIntervalSeconds) {
            this.alertIntervalSeconds = alertIntervalSeconds;
        }
    }
}
