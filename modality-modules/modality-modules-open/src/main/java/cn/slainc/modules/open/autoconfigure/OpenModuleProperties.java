package cn.slainc.modules.open.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "modality.open")
public class OpenModuleProperties {

    private boolean enabled = true;

    private boolean autoCreateTable = true;

    private final Review review = new Review();

    private final App app = new App();

    private final Credential credential = new Credential();

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

    public Review getReview() {
        return review;
    }

    public App getApp() {
        return app;
    }

    public Credential getCredential() {
        return credential;
    }

    public Stats getStats() {
        return stats;
    }

    public static class Review {

        private boolean autoApprove = false;

        public boolean isAutoApprove() {
            return autoApprove;
        }

        public void setAutoApprove(boolean autoApprove) {
            this.autoApprove = autoApprove;
        }
    }

    public static class App {

        private List<String> categories = new ArrayList<>(List.of("工具", "效率", "数据", "安全"));

        public List<String> getCategories() {
            return categories;
        }

        public void setCategories(List<String> categories) {
            this.categories = categories;
        }
    }

    public static class Credential {

        private int rotateGraceHours = 24;

        public int getRotateGraceHours() {
            return rotateGraceHours;
        }

        public void setRotateGraceHours(int rotateGraceHours) {
            this.rotateGraceHours = rotateGraceHours;
        }
    }

    public static class Stats {

        private int trendDays = 30;

        private int topN = 10;

        public int getTrendDays() {
            return trendDays;
        }

        public void setTrendDays(int trendDays) {
            this.trendDays = trendDays;
        }

        public int getTopN() {
            return topN;
        }

        public void setTopN(int topN) {
            this.topN = topN;
        }
    }
}
