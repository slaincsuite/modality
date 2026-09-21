package cn.slainc.modules.upms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "modality.upms")
public class UpmsProperties {

    private boolean enabled = true;

    private boolean autoCreateTable = true;

    private final Account account = new Account();

    private final Stats stats = new Stats();

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

    public Account getAccount() {
        return account;
    }

    public Stats getStats() {
        return stats;
    }

    public static class Account {

        private String defaultPassword = "Modality#123";

        private boolean bcryptCost = true;

        public String getDefaultPassword() {
            return defaultPassword;
        }

        public void setDefaultPassword(String defaultPassword) {
            this.defaultPassword = defaultPassword;
        }

        public boolean isBcryptCost() {
            return bcryptCost;
        }

        public void setBcryptCost(boolean bcryptCost) {
            this.bcryptCost = bcryptCost;
        }
    }

    public static class Stats {

        private int mauDays = 30;

        private int trendDays = 30;

        private int recentLoginLimit = 10;

        public int getMauDays() {
            return mauDays;
        }

        public void setMauDays(int mauDays) {
            this.mauDays = mauDays;
        }

        public int getTrendDays() {
            return trendDays;
        }

        public void setTrendDays(int trendDays) {
            this.trendDays = trendDays;
        }

        public int getRecentLoginLimit() {
            return recentLoginLimit;
        }

        public void setRecentLoginLimit(int recentLoginLimit) {
            this.recentLoginLimit = recentLoginLimit;
        }
    }
}
