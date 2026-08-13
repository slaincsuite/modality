package cn.slainc.utils.json;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonUtils {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private JsonUtils() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object value) {
        return value == null ? null : MAPPER.writeValueAsString(value);
    }

    public static String toPrettyJson(Object value) {
        return value == null ? null : MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    public static <T> T parse(String json, Class<T> type) {
        return json == null || json.isBlank() ? null : MAPPER.readValue(json, type);
    }

    public static <T> T parse(String json, TypeReference<T> typeReference) {
        return json == null || json.isBlank() ? null : MAPPER.readValue(json, typeReference);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseMap(String json) {
        return json == null || json.isBlank() ? new LinkedHashMap<>() : MAPPER.readValue(json, Map.class);
    }

    public static <T> List<T> parseList(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        return MAPPER.readValue(json, MAPPER.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    public static <T> T convert(Object value, Class<T> type) {
        return value == null ? null : MAPPER.convertValue(value, type);
    }

    public static <T> T convert(Object value, TypeReference<T> typeReference) {
        return value == null ? null : MAPPER.convertValue(value, typeReference);
    }

    public static JsonNode readTree(String json) {
        return json == null || json.isBlank() ? null : MAPPER.readTree(json);
    }

    public static boolean isValid(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            MAPPER.readTree(json);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public static ObjectNode createObject() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode createArray() {
        return MAPPER.createArrayNode();
    }

    public static <T> T path(String json, String path) {
        JsonNode node = pathNode(json, path);
        if (node == null || node.isNull()) {
            return null;
        }
        return convert(node, new TypeReference<T>() {
        });
    }

    public static JsonNode pathNode(String json, String path) {
        JsonNode root = readTree(json);
        return root == null ? null : pathNode(root, path);
    }

    public static JsonNode pathNode(JsonNode root, String path) {
        if (root == null || path == null || path.isEmpty()) {
            return root;
        }
        JsonNode resolved = resolvePath(root, path);
        return resolved == null || resolved.isMissingNode() ? null : resolved;
    }

    private static JsonNode resolvePath(JsonNode root, String path) {
        JsonNode current = root;
        int index = 0;
        while (index < path.length() && current != null) {
            char c = path.charAt(index);
            if (c == '.') {
                index++;
                continue;
            }
            if (c == '[') {
                int close = path.indexOf(']', index);
                if (close < 0) {
                    return null;
                }
                int position = Integer.parseInt(path.substring(index + 1, close).trim());
                current = current.path(position);
                index = close + 1;
                continue;
            }
            int next = index;
            while (next < path.length() && path.charAt(next) != '.' && path.charAt(next) != '[') {
                next++;
            }
            current = current.path(path.substring(index, next));
            index = next;
        }
        return current;
    }

    public static String merge(String left, String right) {
        Map<String, Object> merged = mergeObjects(parseMap(left), parseMap(right));
        return toJson(merged);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> mergeObjects(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (left != null) {
            result.putAll(left);
        }
        if (right == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : right.entrySet()) {
            Object existing = result.get(entry.getKey());
            Object incoming = entry.getValue();
            if (existing instanceof Map && incoming instanceof Map) {
                result.put(entry.getKey(),
                        mergeObjects((Map<String, Object>) existing, (Map<String, Object>) incoming));
            } else {
                result.put(entry.getKey(), incoming);
            }
        }
        return result;
    }

    public static Map<String, Object> compare(String left, String right) {
        Map<String, Object> diffs = new LinkedHashMap<>();
        collectDiffs("", parseMap(left), parseMap(right), diffs);
        return diffs;
    }

    @SuppressWarnings("unchecked")
    private static void collectDiffs(String prefix, Map<String, Object> left, Map<String, Object> right,
                                     Map<String, Object> diffs) {
        if (left == null && right == null) {
            return;
        }
        Map<String, Object> leftMap = left == null ? new LinkedHashMap<>() : left;
        Map<String, Object> rightMap = right == null ? new LinkedHashMap<>() : right;
        java.util.Set<String> keys = new java.util.LinkedHashSet<>(leftMap.keySet());
        keys.addAll(rightMap.keySet());
        for (String key : keys) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object leftValue = leftMap.get(key);
            Object rightValue = rightMap.get(key);
            if (leftValue instanceof Map && rightValue instanceof Map) {
                collectDiffs(path, (Map<String, Object>) leftValue, (Map<String, Object>) rightValue, diffs);
                continue;
            }
            if (!java.util.Objects.equals(leftValue, rightValue)) {
                diffs.put(path, new Object[]{leftValue, rightValue});
            }
        }
    }

    public static Map<String, Object> flatten(String json) {
        return flattenNode(readTree(json), "");
    }

    private static Map<String, Object> flattenNode(JsonNode node, String prefix) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (node == null || node.isNull()) {
            return result;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
                result.putAll(flattenNode(field.getValue(), key));
            }
            return result;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                result.putAll(flattenNode(node.get(i), prefix + "[" + i + "]"));
            }
            return result;
        }
        result.put(prefix, convert(node, Object.class));
        return result;
    }
}
