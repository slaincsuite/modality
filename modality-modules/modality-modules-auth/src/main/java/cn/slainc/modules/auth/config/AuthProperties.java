package cn.slainc.modules.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "modality.auth")
public class AuthProperties {

    private boolean enabled = true;

    private Captcha captcha = new Captcha();

    private Qr qr = new Qr();

    private Renewal renewal = new Renewal();

    private RememberMe rememberMe = new RememberMe();

    private Third third = new Third();

    private Safe safe = new Safe();

    private Closure closure = new Closure();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Captcha getCaptcha() {
        return captcha;
    }

    public void setCaptcha(Captcha captcha) {
        this.captcha = captcha;
    }

    public Qr getQr() {
        return qr;
    }

    public void setQr(Qr qr) {
        this.qr = qr;
    }

    public Renewal getRenewal() {
        return renewal;
    }

    public void setRenewal(Renewal renewal) {
        this.renewal = renewal;
    }

    public RememberMe getRememberMe() {
        return rememberMe;
    }

    public void setRememberMe(RememberMe rememberMe) {
        this.rememberMe = rememberMe;
    }

    public Third getThird() {
        return third;
    }

    public void setThird(Third third) {
        this.third = third;
    }

    public Safe getSafe() {
        return safe;
    }

    public void setSafe(Safe safe) {
        this.safe = safe;
    }

    public Closure getClosure() {
        return closure;
    }

    public void setClosure(Closure closure) {
        this.closure = closure;
    }

    public static class Captcha {

        private boolean enabled = false;

        private String channel = "WEB";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }
    }

    public static class Qr {

        private long ttlSeconds = 300;

        private String contentPrefix = "modality://qr-login/";

        private int width = 240;

        private int height = 240;

        private boolean logoEnabled = true;

        private String logoText = "M";

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public String getContentPrefix() {
            return contentPrefix;
        }

        public void setContentPrefix(String contentPrefix) {
            this.contentPrefix = contentPrefix;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public boolean isLogoEnabled() {
            return logoEnabled;
        }

        public void setLogoEnabled(boolean logoEnabled) {
            this.logoEnabled = logoEnabled;
        }

        public String getLogoText() {
            return logoText;
        }

        public void setLogoText(String logoText) {
            this.logoText = logoText;
        }
    }

    public static class Renewal {

        private boolean enabled = true;

        private long thresholdSeconds = 600;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getThresholdSeconds() {
            return thresholdSeconds;
        }

        public void setThresholdSeconds(long thresholdSeconds) {
            this.thresholdSeconds = thresholdSeconds;
        }
    }

    public static class RememberMe {

        private long ttlSeconds = 2592000;

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }

    public static class Third {

        private String loginType = "TENANT_USER";

        private long bindTicketTtlSeconds = 300;

        public String getLoginType() {
            return loginType;
        }

        public void setLoginType(String loginType) {
            this.loginType = loginType;
        }

        public long getBindTicketTtlSeconds() {
            return bindTicketTtlSeconds;
        }

        public void setBindTicketTtlSeconds(long bindTicketTtlSeconds) {
            this.bindTicketTtlSeconds = bindTicketTtlSeconds;
        }
    }

    public static class Safe {

        private long ttlSeconds = 300;

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }

    public static class Closure {

        private boolean enabled = true;

        private boolean autoCreateTable = true;

        private long coolDownSeconds = 604800;

        private long cleanupIntervalSeconds = 3600;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAutoCreateTable() {
            return autoCreateTable;
        }

        public void setAutoCreateTable(boolean autoCreateTable) {
            this.autoCreateTable = autoCreateTable;
        }

        public long getCoolDownSeconds() {
            return coolDownSeconds;
        }

        public void setCoolDownSeconds(long coolDownSeconds) {
            this.coolDownSeconds = coolDownSeconds;
        }

        public long getCleanupIntervalSeconds() {
            return cleanupIntervalSeconds;
        }

        public void setCleanupIntervalSeconds(long cleanupIntervalSeconds) {
            this.cleanupIntervalSeconds = cleanupIntervalSeconds;
        }
    }
}
