package cn.slainc.sso.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "modality.sso")
public class SsoProperties {

    private boolean enabled = true;

    private String loginType = "TENANT_USER";

    private Server server = new Server();

    private Client client = new Client();

    private ClientStore clientStore = new ClientStore();

    private List<ClientItem> clients = new ArrayList<>();

    public static class Server {

        private String secretKey = "";

        private long ticketTimeoutSeconds = 30;

        private String allowUrl = "";

        private boolean slo = true;

        private boolean checkSign = true;

        private boolean allowAnonClient = false;

        private String homeRoute = "";

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public long getTicketTimeoutSeconds() {
            return ticketTimeoutSeconds;
        }

        public void setTicketTimeoutSeconds(long ticketTimeoutSeconds) {
            this.ticketTimeoutSeconds = ticketTimeoutSeconds;
        }

        public String getAllowUrl() {
            return allowUrl;
        }

        public void setAllowUrl(String allowUrl) {
            this.allowUrl = allowUrl;
        }

        public boolean isSlo() {
            return slo;
        }

        public void setSlo(boolean slo) {
            this.slo = slo;
        }

        public boolean isCheckSign() {
            return checkSign;
        }

        public void setCheckSign(boolean checkSign) {
            this.checkSign = checkSign;
        }

        public boolean isAllowAnonClient() {
            return allowAnonClient;
        }

        public void setAllowAnonClient(boolean allowAnonClient) {
            this.allowAnonClient = allowAnonClient;
        }

        public String getHomeRoute() {
            return homeRoute;
        }

        public void setHomeRoute(String homeRoute) {
            this.homeRoute = homeRoute;
        }
    }

    public static class Client {

        private boolean enabled = false;

        private String client = "";

        private String serverUrl = "";

        private String secretKey = "";

        private String loginType = "";

        private boolean slo = true;

        private boolean checkSign = true;

        private boolean regLogoutCall = false;

        public String getLoginType() {
            return loginType;
        }

        public void setLoginType(String loginType) {
            this.loginType = loginType;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getClient() {
            return client;
        }

        public void setClient(String client) {
            this.client = client;
        }

        public String getServerUrl() {
            return serverUrl;
        }

        public void setServerUrl(String serverUrl) {
            this.serverUrl = serverUrl;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public boolean isSlo() {
            return slo;
        }

        public void setSlo(boolean slo) {
            this.slo = slo;
        }

        public boolean isCheckSign() {
            return checkSign;
        }

        public void setCheckSign(boolean checkSign) {
            this.checkSign = checkSign;
        }

        public boolean isRegLogoutCall() {
            return regLogoutCall;
        }

        public void setRegLogoutCall(boolean regLogoutCall) {
            this.regLogoutCall = regLogoutCall;
        }
    }

    public static class ClientStore {

        private String store = "jdbc";

        private String tableName = "t_sso_client";

        private boolean autoCreateTable = true;

        private long cacheSeconds = 10;

        public String getStore() {
            return store;
        }

        public void setStore(String store) {
            this.store = store;
        }

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

        public long getCacheSeconds() {
            return cacheSeconds;
        }

        public void setCacheSeconds(long cacheSeconds) {
            this.cacheSeconds = cacheSeconds;
        }
    }

    public static class ClientItem {

        private String client;

        private String secretKey = "";

        private String allowUrl = "";

        private String baseUrl = "";

        private String pushUrl = "/sso/pushC";

        private boolean push = true;

        private boolean slo = true;

        public String getClient() {
            return client;
        }

        public void setClient(String client) {
            this.client = client;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getAllowUrl() {
            return allowUrl;
        }

        public void setAllowUrl(String allowUrl) {
            this.allowUrl = allowUrl;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getPushUrl() {
            return pushUrl;
        }

        public void setPushUrl(String pushUrl) {
            this.pushUrl = pushUrl;
        }

        public boolean isPush() {
            return push;
        }

        public void setPush(boolean push) {
            this.push = push;
        }

        public boolean isSlo() {
            return slo;
        }

        public void setSlo(boolean slo) {
            this.slo = slo;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLoginType() {
        return loginType;
    }

    public void setLoginType(String loginType) {
        this.loginType = loginType;
    }

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public ClientStore getClientStore() {
        return clientStore;
    }

    public void setClientStore(ClientStore clientStore) {
        this.clientStore = clientStore;
    }

    public List<ClientItem> getClients() {
        return clients;
    }

    public void setClients(List<ClientItem> clients) {
        this.clients = clients;
    }
}
