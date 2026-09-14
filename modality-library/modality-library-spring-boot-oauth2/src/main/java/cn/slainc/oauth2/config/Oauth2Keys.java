package cn.slainc.oauth2.config;

public final class Oauth2Keys {

    public static final String PREFIX = "modality:oauth2:";

    public static final String SCOPE_CONFIG_KEY = "modality.oauth2.scopes";

    public static final String CLIENT_REFRESH_KEY = "modality.oauth2.client.refresh";

    public static String pkceKey(String code) {
        return PREFIX + "pkce:" + code;
    }

    public static String stateKey(String state) {
        return PREFIX + "state:" + state;
    }

    public static String clientRateKey(String clientId) {
        return PREFIX + "client:ratelimit:" + clientId;
    }

    private Oauth2Keys() {
    }
}
