package cn.slainc.apikey.guard;

public class ApiKeyStatsKeys {

    public static final String TOTAL_FIELD = "total";

    public static final String LAST_USED_FIELD = "last-used";

    private ApiKeyStatsKeys() {
    }

    public static String fingerprint(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        return apiKey.length() > 8 ? apiKey.substring(0, 8) : apiKey;
    }
}
