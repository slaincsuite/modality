package cn.slainc.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "modality.security")
public class SecurityProperties {

    private boolean enabled = true;

    private String typeHeader = "X-Login-Type";

    private String defaultType = "TENANT_USER";

    private String deviceIdHeader = "X-Device-Id";

    private int permissionCacheTtlSeconds = 300;

    private final Filter filter = new Filter();

    private final Origin origin = new Origin();

    private final Session session = new Session();

    private final Device device = new Device();

    private final Cookie cookie = new Cookie();

    private final Dao dao = new Dao();

    private final Restriction restriction = new Restriction();

    private final Password password = new Password();

    private final Lockout lockout = new Lockout();

    private final List<AccountItem> accounts = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTypeHeader() {
        return typeHeader;
    }

    public void setTypeHeader(String typeHeader) {
        this.typeHeader = typeHeader;
    }

    public String getDefaultType() {
        return defaultType;
    }

    public void setDefaultType(String defaultType) {
        this.defaultType = defaultType;
    }

    public String getDeviceIdHeader() {
        return deviceIdHeader;
    }

    public void setDeviceIdHeader(String deviceIdHeader) {
        this.deviceIdHeader = deviceIdHeader;
    }

    public int getPermissionCacheTtlSeconds() {
        return permissionCacheTtlSeconds;
    }

    public void setPermissionCacheTtlSeconds(int permissionCacheTtlSeconds) {
        this.permissionCacheTtlSeconds = permissionCacheTtlSeconds;
    }

    public Filter getFilter() {
        return filter;
    }

    public Origin getOrigin() {
        return origin;
    }

    public Session getSession() {
        return session;
    }

    public Device getDevice() {
        return device;
    }

    public Cookie getCookie() {
        return cookie;
    }

    public Dao getDao() {
        return dao;
    }

    public Restriction getRestriction() {
        return restriction;
    }

    public Password getPassword() {
        return password;
    }

    public Lockout getLockout() {
        return lockout;
    }

    public List<AccountItem> getAccounts() {
        return accounts;
    }

    public static class Filter {

        private List<String> excludePatterns = List.of("/actuator/health", "/actuator/health/**", "/actuator/info",
                "/favicon.ico", "/error", "/captcha/**", "/auth/login", "/tenant/login-options", "/auth/refresh",
                "/auth/logout",
                "/auth/qrcode/**", "/auth/third/**", "/auth/token/refresh", "/oauth2/jwks", "/oauth2/token",
                "/oauth2/userinfo", "/sso/**");

        private List<String> blacklistPatterns = new ArrayList<>();

        private List<String> protectedPatterns = List.of("/**");

        public List<String> getExcludePatterns() {
            return excludePatterns;
        }

        public void setExcludePatterns(List<String> excludePatterns) {
            this.excludePatterns = excludePatterns;
        }

        public List<String> getBlacklistPatterns() {
            return blacklistPatterns;
        }

        public void setBlacklistPatterns(List<String> blacklistPatterns) {
            this.blacklistPatterns = blacklistPatterns;
        }

        public List<String> getProtectedPatterns() {
            return protectedPatterns;
        }

        public void setProtectedPatterns(List<String> protectedPatterns) {
            this.protectedPatterns = protectedPatterns;
        }
    }

    public static class Origin {

        private boolean enabled = true;

        private List<String> allowedOrigins = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Session {

        private long timeoutPlatformAdmin = 7200;

        private long timeoutPlatformUser = 7200;

        private long timeoutTenantAdmin = 7200;

        private long timeoutTenantUser = 7200;

        private long timeoutApp = 7200;

        private long timeoutService = 43200;

        private long timeoutGuest = 7200;

        private long activeTimeout = 1800;

        public long getTimeoutPlatformAdmin() {
            return timeoutPlatformAdmin;
        }

        public void setTimeoutPlatformAdmin(long timeoutPlatformAdmin) {
            this.timeoutPlatformAdmin = timeoutPlatformAdmin;
        }

        public long getTimeoutPlatformUser() {
            return timeoutPlatformUser;
        }

        public void setTimeoutPlatformUser(long timeoutPlatformUser) {
            this.timeoutPlatformUser = timeoutPlatformUser;
        }

        public long getTimeoutTenantAdmin() {
            return timeoutTenantAdmin;
        }

        public void setTimeoutTenantAdmin(long timeoutTenantAdmin) {
            this.timeoutTenantAdmin = timeoutTenantAdmin;
        }

        public long getTimeoutTenantUser() {
            return timeoutTenantUser;
        }

        public void setTimeoutTenantUser(long timeoutTenantUser) {
            this.timeoutTenantUser = timeoutTenantUser;
        }

        public long getTimeoutApp() {
            return timeoutApp;
        }

        public void setTimeoutApp(long timeoutApp) {
            this.timeoutApp = timeoutApp;
        }

        public long getTimeoutService() {
            return timeoutService;
        }

        public void setTimeoutService(long timeoutService) {
            this.timeoutService = timeoutService;
        }

        public long getTimeoutGuest() {
            return timeoutGuest;
        }

        public void setTimeoutGuest(long timeoutGuest) {
            this.timeoutGuest = timeoutGuest;
        }

        public long getActiveTimeout() {
            return activeTimeout;
        }

        public void setActiveTimeout(long activeTimeout) {
            this.activeTimeout = activeTimeout;
        }
    }

    public static class Device {

        private int maxCountPerType = 5;

        private DeviceOverflowMode overflowMode = DeviceOverflowMode.KICK_OLDEST;

        private boolean singleSessionPerType = false;

        public int getMaxCountPerType() {
            return maxCountPerType;
        }

        public void setMaxCountPerType(int maxCountPerType) {
            this.maxCountPerType = maxCountPerType;
        }

        public DeviceOverflowMode getOverflowMode() {
            return overflowMode;
        }

        public void setOverflowMode(DeviceOverflowMode overflowMode) {
            this.overflowMode = overflowMode;
        }

        public boolean isSingleSessionPerType() {
            return singleSessionPerType;
        }

        public void setSingleSessionPerType(boolean singleSessionPerType) {
            this.singleSessionPerType = singleSessionPerType;
        }
    }

    public static class Cookie {

        private String sameSite = "Lax";

        private boolean secure = true;

        public String getSameSite() {
            return sameSite;
        }

        public void setSameSite(String sameSite) {
            this.sameSite = sameSite;
        }

        public boolean isSecure() {
            return secure;
        }

        public void setSecure(boolean secure) {
            this.secure = secure;
        }
    }

    public static class Dao {

        private int retrySeconds = 30;

        public int getRetrySeconds() {
            return retrySeconds;
        }

        public void setRetrySeconds(int retrySeconds) {
            this.retrySeconds = retrySeconds;
        }
    }

    public static class Restriction {

        private List<String> ipWhitelist = new ArrayList<>();

        private List<String> ipBlacklist = new ArrayList<>();

        private String timeStart = "";

        private String timeEnd = "";

        private List<String> allowedDeviceTypes = new ArrayList<>();

        public List<String> getIpWhitelist() {
            return ipWhitelist;
        }

        public void setIpWhitelist(List<String> ipWhitelist) {
            this.ipWhitelist = ipWhitelist;
        }

        public List<String> getIpBlacklist() {
            return ipBlacklist;
        }

        public void setIpBlacklist(List<String> ipBlacklist) {
            this.ipBlacklist = ipBlacklist;
        }

        public String getTimeStart() {
            return timeStart;
        }

        public void setTimeStart(String timeStart) {
            this.timeStart = timeStart;
        }

        public String getTimeEnd() {
            return timeEnd;
        }

        public void setTimeEnd(String timeEnd) {
            this.timeEnd = timeEnd;
        }

        public List<String> getAllowedDeviceTypes() {
            return allowedDeviceTypes;
        }

        public void setAllowedDeviceTypes(List<String> allowedDeviceTypes) {
            this.allowedDeviceTypes = allowedDeviceTypes;
        }
    }

    public static class Password {

        private int minLength = 8;

        private int requireClasses = 3;

        private int historySize = 5;

        private int maxAgeDays = 90;

        public int getMinLength() {
            return minLength;
        }

        public void setMinLength(int minLength) {
            this.minLength = minLength;
        }

        public int getRequireClasses() {
            return requireClasses;
        }

        public void setRequireClasses(int requireClasses) {
            this.requireClasses = requireClasses;
        }

        public int getHistorySize() {
            return historySize;
        }

        public void setHistorySize(int historySize) {
            this.historySize = historySize;
        }

        public int getMaxAgeDays() {
            return maxAgeDays;
        }

        public void setMaxAgeDays(int maxAgeDays) {
            this.maxAgeDays = maxAgeDays;
        }
    }

    public static class Lockout {

        private int maxFailures = 5;

        private int windowSeconds = 600;

        private int lockMinutes = 30;

        public int getMaxFailures() {
            return maxFailures;
        }

        public void setMaxFailures(int maxFailures) {
            this.maxFailures = maxFailures;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public int getLockMinutes() {
            return lockMinutes;
        }

        public void setLockMinutes(int lockMinutes) {
            this.lockMinutes = lockMinutes;
        }
    }

    public static class AccountItem {

        private String username;

        private String passwordBcrypt;

        private Long userId;

        private String type = "TENANT_USER";

        private boolean enabled = true;

        private boolean mustChangePassword = false;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPasswordBcrypt() {
            return passwordBcrypt;
        }

        public void setPasswordBcrypt(String passwordBcrypt) {
            this.passwordBcrypt = passwordBcrypt;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isMustChangePassword() {
            return mustChangePassword;
        }

        public void setMustChangePassword(boolean mustChangePassword) {
            this.mustChangePassword = mustChangePassword;
        }
    }
}
