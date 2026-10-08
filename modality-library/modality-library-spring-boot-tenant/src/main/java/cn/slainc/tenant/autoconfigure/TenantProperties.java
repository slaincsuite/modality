package cn.slainc.tenant.autoconfigure;

import cn.slainc.core.constant.HttpHeaders;
import cn.slainc.tenant.isolation.TenantIsolationStrategy;
import cn.slainc.tenant.lifecycle.TenantDataTemplate;
import cn.slainc.tenant.quota.TenantQuota;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "modality.tenant")
public class TenantProperties {

    private boolean enabled = true;

    private final Filter filter = new Filter();

    private final Resolver resolver = new Resolver();

    private final Isolation isolation = new Isolation();

    private final Quota quota = new Quota();

    private final Lifecycle lifecycle = new Lifecycle();

    private final CacheSettings cache = new CacheSettings();

    private final Service service = new Service();

    private List<String> ignoreTables = new ArrayList<>(List.of("t_tenant", "t_tenant_quota", "t_tenant_package"));

    private List<String> ignoreTablePrefixes = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Filter getFilter() {
        return filter;
    }

    public Resolver getResolver() {
        return resolver;
    }

    public Isolation getIsolation() {
        return isolation;
    }

    public Quota getQuota() {
        return quota;
    }

    public Lifecycle getLifecycle() {
        return lifecycle;
    }

    public CacheSettings getCache() {
        return cache;
    }

    public Service getService() {
        return service;
    }

    public List<String> getIgnoreTables() {
        return ignoreTables;
    }

    public void setIgnoreTables(List<String> ignoreTables) {
        this.ignoreTables = ignoreTables == null ? new ArrayList<>() : ignoreTables;
    }

    public List<String> getIgnoreTablePrefixes() {
        return ignoreTablePrefixes;
    }

    public void setIgnoreTablePrefixes(List<String> ignoreTablePrefixes) {
        this.ignoreTablePrefixes = ignoreTablePrefixes == null ? new ArrayList<>() : ignoreTablePrefixes;
    }

    public static class Filter {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Service {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Resolver {

        private final Header header = new Header();

        private final Domain domain = new Domain();

        private final Path path = new Path();

        private final Param param = new Param();

        private final Session session = new Session();

        public Header getHeader() {
            return header;
        }

        public Domain getDomain() {
            return domain;
        }

        public Path getPath() {
            return path;
        }

        public Param getParam() {
            return param;
        }

        public Session getSession() {
            return session;
        }

        public static class Header {

            private boolean enabled = false;

            private String name = HttpHeaders.TENANT_ID;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }
        }

        public static class Domain {

            private boolean enabled = true;

            private String rootDomain;

            private List<String> ignoredSubdomains = new ArrayList<>(List.of("www", "api", "admin"));

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getRootDomain() {
                return rootDomain;
            }

            public void setRootDomain(String rootDomain) {
                this.rootDomain = rootDomain;
            }

            public List<String> getIgnoredSubdomains() {
                return ignoredSubdomains;
            }

            public void setIgnoredSubdomains(List<String> ignoredSubdomains) {
                this.ignoredSubdomains = ignoredSubdomains == null ? new ArrayList<>() : ignoredSubdomains;
            }
        }

        public static class Path {

            private boolean enabled = true;

            private String prefix = "/api";

            private int index = 0;

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

            public int getIndex() {
                return index;
            }

            public void setIndex(int index) {
                this.index = index;
            }
        }

        public static class Param {

            private boolean enabled = true;

            private String name = "tenantId";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }
        }

        public static class Session {

            private boolean enabled = true;

            private String attribute = "tenantId";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getAttribute() {
                return attribute;
            }

            public void setAttribute(String attribute) {
                this.attribute = attribute;
            }
        }
    }

    public static class Isolation {

        private boolean enabled = true;

        private TenantIsolationStrategy strategy = TenantIsolationStrategy.ROW;

        private final Schema schema = new Schema();

        private final Database database = new Database();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public TenantIsolationStrategy getStrategy() {
            return strategy;
        }

        public void setStrategy(TenantIsolationStrategy strategy) {
            this.strategy = strategy;
        }

        public Schema getSchema() {
            return schema;
        }

        public Database getDatabase() {
            return database;
        }

        public static class Schema {

            private boolean enabled = true;

            private String prefix = "tenant_";

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
        }

        public static class Database {

            private boolean enabled = true;

            private String groupName = "tenant";

            private String urlTemplate;

            private String usernameTemplate;

            private String passwordTemplate;

            private String driverClassName;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getGroupName() {
                return groupName;
            }

            public void setGroupName(String groupName) {
                this.groupName = groupName;
            }

            public String getUrlTemplate() {
                return urlTemplate;
            }

            public void setUrlTemplate(String urlTemplate) {
                this.urlTemplate = urlTemplate;
            }

            public String getUsernameTemplate() {
                return usernameTemplate;
            }

            public void setUsernameTemplate(String usernameTemplate) {
                this.usernameTemplate = usernameTemplate;
            }

            public String getPasswordTemplate() {
                return passwordTemplate;
            }

            public void setPasswordTemplate(String passwordTemplate) {
                this.passwordTemplate = passwordTemplate;
            }

            public String getDriverClassName() {
                return driverClassName;
            }

            public void setDriverClassName(String driverClassName) {
                this.driverClassName = driverClassName;
            }
        }
    }

    public static class Quota {

        private boolean enabled = true;

        private double warningRatio = 0.8D;

        private final TenantQuota defaults = new TenantQuota();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getWarningRatio() {
            return warningRatio;
        }

        public void setWarningRatio(double warningRatio) {
            this.warningRatio = warningRatio;
        }

        public TenantQuota getDefaults() {
            return defaults;
        }
    }

    public static class Lifecycle {

        private boolean enabled = true;

        private boolean initializeOnCreate = true;

        private boolean removeIsolationOnDelete = false;

        private int trialDays = 30;

        private int defaultExpireDays = 0;

        private final TenantDataTemplate data = new TenantDataTemplate();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isInitializeOnCreate() {
            return initializeOnCreate;
        }

        public void setInitializeOnCreate(boolean initializeOnCreate) {
            this.initializeOnCreate = initializeOnCreate;
        }

        public boolean isRemoveIsolationOnDelete() {
            return removeIsolationOnDelete;
        }

        public void setRemoveIsolationOnDelete(boolean removeIsolationOnDelete) {
            this.removeIsolationOnDelete = removeIsolationOnDelete;
        }

        public int getTrialDays() {
            return trialDays;
        }

        public void setTrialDays(int trialDays) {
            this.trialDays = trialDays;
        }

        public int getDefaultExpireDays() {
            return defaultExpireDays;
        }

        public void setDefaultExpireDays(int defaultExpireDays) {
            this.defaultExpireDays = defaultExpireDays;
        }

        public TenantDataTemplate getData() {
            return data;
        }
    }

    public static class CacheSettings {

        private long maximumSize = 10_000L;

        private Duration expireAfterWrite = Duration.ofMinutes(30L);

        public long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }

        public Duration getExpireAfterWrite() {
            return expireAfterWrite;
        }

        public void setExpireAfterWrite(Duration expireAfterWrite) {
            this.expireAfterWrite = expireAfterWrite;
        }
    }
}
