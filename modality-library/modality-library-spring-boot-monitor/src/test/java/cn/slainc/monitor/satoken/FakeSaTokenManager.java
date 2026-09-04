package cn.slainc.monitor.satoken;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FakeSaTokenManager {

    private static final Map<String, String> KEYS = new LinkedHashMap<>();

    private FakeSaTokenManager() {
    }

    public static void reset(String... keys) {
        KEYS.clear();
        for (String key : keys) {
            KEYS.put(key, "value");
        }
    }

    public static Object getSaTokenDao() {
        return new FakeSaTokenDao();
    }

    public static class FakeSaTokenDao {

        public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
            List<String> matched = new ArrayList<>();
            for (String key : KEYS.keySet()) {
                if (key.startsWith(prefix)) {
                    matched.add(key);
                }
            }
            return matched;
        }
    }
}
