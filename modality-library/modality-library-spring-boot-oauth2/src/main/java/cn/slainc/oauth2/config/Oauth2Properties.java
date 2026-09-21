package cn.slainc.oauth2.config;

import cn.slainc.oauth2.scope.ScopeDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "modality.oauth2")
public class Oauth2Properties {

    private boolean enabled = true;

    private String loginType = "TENANT_USER";

    private long codeTimeoutSeconds = 300;

    private long accessTokenTimeoutSeconds = 7200;

    private long refreshTokenTimeoutSeconds = 2592000;

    private long clientTokenTimeoutSeconds = 2592000;

    private boolean newRefresh = true;

    private Client client = new Client();

    private Pkce pkce = new Pkce();

    private State state = new State();

    private Oidc oidc = new Oidc();

    private Guard guard = new Guard();

    private List<ScopeDefinition> scopes = new ArrayList<>();

    public static class Client {

        private String store = "jdbc";

        private String tableName = "t_oauth_client";

        private String consentTableName = "t_consent_record";

        private boolean autoCreateTable = true;

        private long cacheSeconds = 10;

        private String secretEncryptionKey;

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

        public String getConsentTableName() {
            return consentTableName;
        }

        public void setConsentTableName(String consentTableName) {
            this.consentTableName = consentTableName;
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

        public String getSecretEncryptionKey() {
            return secretEncryptionKey;
        }

        public void setSecretEncryptionKey(String secretEncryptionKey) {
            this.secretEncryptionKey = secretEncryptionKey;
        }
    }

    public static class Pkce {

        private boolean enabled = true;

        private boolean required = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }
    }

    public static class State {

        private long timeoutSeconds = 300;

        public long getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Oidc {

        private boolean enabled = true;

        private String issuer;

        private long idTokenSeconds = 600;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public long getIdTokenSeconds() {
            return idTokenSeconds;
        }

        public void setIdTokenSeconds(long idTokenSeconds) {
            this.idTokenSeconds = idTokenSeconds;
        }
    }

    public static class Guard {

        private boolean enabled = true;

        private long defaultQpsLimit = 0;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getDefaultQpsLimit() {
            return defaultQpsLimit;
        }

        public void setDefaultQpsLimit(long defaultQpsLimit) {
            this.defaultQpsLimit = defaultQpsLimit;
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

    public long getCodeTimeoutSeconds() {
        return codeTimeoutSeconds;
    }

    public void setCodeTimeoutSeconds(long codeTimeoutSeconds) {
        this.codeTimeoutSeconds = codeTimeoutSeconds;
    }

    public long getAccessTokenTimeoutSeconds() {
        return accessTokenTimeoutSeconds;
    }

    public void setAccessTokenTimeoutSeconds(long accessTokenTimeoutSeconds) {
        this.accessTokenTimeoutSeconds = accessTokenTimeoutSeconds;
    }

    public long getRefreshTokenTimeoutSeconds() {
        return refreshTokenTimeoutSeconds;
    }

    public void setRefreshTokenTimeoutSeconds(long refreshTokenTimeoutSeconds) {
        this.refreshTokenTimeoutSeconds = refreshTokenTimeoutSeconds;
    }

    public long getClientTokenTimeoutSeconds() {
        return clientTokenTimeoutSeconds;
    }

    public void setClientTokenTimeoutSeconds(long clientTokenTimeoutSeconds) {
        this.clientTokenTimeoutSeconds = clientTokenTimeoutSeconds;
    }

    public boolean isNewRefresh() {
        return newRefresh;
    }

    public void setNewRefresh(boolean newRefresh) {
        this.newRefresh = newRefresh;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public Pkce getPkce() {
        return pkce;
    }

    public void setPkce(Pkce pkce) {
        this.pkce = pkce;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Oidc getOidc() {
        return oidc;
    }

    public void setOidc(Oidc oidc) {
        this.oidc = oidc;
    }

    public Guard getGuard() {
        return guard;
    }

    public void setGuard(Guard guard) {
        this.guard = guard;
    }

    public List<ScopeDefinition> getScopes() {
        return scopes;
    }

    public void setScopes(List<ScopeDefinition> scopes) {
        this.scopes = scopes;
    }
}
