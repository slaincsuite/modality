package cn.slainc.apikey.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

public final class ApiKeyJson {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyJson.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<Map<String, Object>> EXTRA_TYPE = new TypeReference<>() {
    };

    private ApiKeyJson() {
    }

    public static String writeExtra(Map<String, Object> extra) {
        if (extra == null || extra.isEmpty()) {
            return null;
        }
        return MAPPER.writeValueAsString(extra);
    }

    public static Map<String, Object> parseExtra(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, EXTRA_TYPE);
        } catch (Exception e) {
            log.warn("API Key 扩展数据 JSON 解析失败，按空处理，原因={}", e.getMessage());
            return null;
        }
    }
}
