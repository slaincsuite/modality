package cn.slainc.thirdauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "modality.thirdauth")
public class ThirdAuthProperties {

    private boolean enabled = true;

    private String store = "jdbc";

    private long stateTtlSeconds = 300;

    private long qrTicketTtlSeconds = 300;

    private long requestTimeoutSeconds = 10;

    private final Jdbc jdbc = new Jdbc();

    private final Map<String, Platform> platform = new LinkedHashMap<>();

    public static class Jdbc {

        private String tableName = "t_third_account_binding";

        private boolean autoCreateTable = true;

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public boolean isAutoCreateTable() {
            return autoCreateTable;
        }

        public void setAutoCreateTable(boolean autoCreateTable) {
            this.autoCreateTable = autoCreateTable;
        }
    }

    public static class Platform {

        private boolean enabled;

        private String appKey;

        private String appSecret;

        private String redirectUri;

        private String callbackBase;

        private String agentId;

        private Scene scene = Scene.WEB;

        public enum Scene {
            WEB,
            SCAN
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAppKey() {
            return appKey;
        }

        public void setAppKey(String appKey) {
            this.appKey = appKey;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }

        public String getCallbackBase() {
            return callbackBase;
        }

        public void setCallbackBase(String callbackBase) {
            this.callbackBase = callbackBase;
        }

        public String getAgentId() {
            return agentId;
        }

        public void setAgentId(String agentId) {
            this.agentId = agentId;
        }

        public Scene getScene() {
            return scene;
        }

        public void setScene(Scene scene) {
            this.scene = scene;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public long getStateTtlSeconds() {
        return stateTtlSeconds;
    }

    public void setStateTtlSeconds(long stateTtlSeconds) {
        this.stateTtlSeconds = stateTtlSeconds;
    }

    public long getQrTicketTtlSeconds() {
        return qrTicketTtlSeconds;
    }

    public void setQrTicketTtlSeconds(long qrTicketTtlSeconds) {
        this.qrTicketTtlSeconds = qrTicketTtlSeconds;
    }

    public long getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(long requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    public Jdbc getJdbc() {
        return jdbc;
    }

    public Map<String, Platform> getPlatform() {
        return platform;
    }
}
