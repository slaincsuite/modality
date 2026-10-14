package cn.slainc.changeaudit.support;

import cn.slainc.changeaudit.api.FieldDiffEngine;
import cn.slainc.changeaudit.autoconfigure.ChangeAuditConfigReader;
import cn.slainc.changeaudit.support.FieldChange;
import cn.slainc.changeaudit.support.SensitiveFields;
import cn.slainc.utils.collection.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public class FieldDiffer implements FieldDiffEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(FieldDiffer.class);

    private static final String SINGLE_VALUE_FIELD = "value";

    private final ObjectMapper objectMapper;

    private final ChangeAuditConfigReader reader;

    public FieldDiffer(ObjectMapper objectMapper, ChangeAuditConfigReader reader) {
        this.objectMapper = rawValueMapper(objectMapper);
        this.reader = reader == null ? new ChangeAuditConfigReader(null, null) : reader;
    }

    public List<FieldChange> diff(Object before, Object after) {
        Class<?> type = before != null ? before.getClass() : (after == null ? null : after.getClass());
        if (type == null) {
            return List.of();
        }
        JsonNode beforeNode = toTree(before);
        JsonNode afterNode = toTree(after);
        List<FieldChange> changes = new ArrayList<>();
        for (String name : propertyNames(beforeNode, afterNode)) {
            if (ignored(name)) {
                continue;
            }
            JsonNode beforeValue = property(beforeNode, name);
            JsonNode afterValue = property(afterNode, name);
            if (same(beforeValue, afterValue)) {
                continue;
            }
            changes.add(new FieldChange(name, text(type, name, beforeValue), text(type, name, afterValue)));
        }
        return changes;
    }

    boolean ignoredField(String name) {
        return ignored(name);
    }

    int maxValueLength() {
        return Math.max(reader.maxValueLength(), 1);
    }

    public String serialize(List<FieldChange> changes) {
        if (CollectionUtils.isEmpty(changes)) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (RuntimeException e) {
            LOGGER.warn("序列化变更字段列表失败: {}", e.getMessage());
            return "[]";
        }
    }

    private JsonNode toTree(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.valueToTree(value);
        } catch (RuntimeException e) {
            LOGGER.warn("序列化 {} 用于变更比对失败: {}", value.getClass().getName(), e.getMessage());
            return null;
        }
    }

    private static List<String> propertyNames(JsonNode beforeNode, JsonNode afterNode) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.addAll(namesOf(beforeNode));
        names.addAll(namesOf(afterNode));
        return List.copyOf(names);
    }

    private static List<String> namesOf(JsonNode node) {
        if (node == null) {
            return List.of();
        }
        return node.isObject() ? List.copyOf(node.propertyNames()) : List.of(SINGLE_VALUE_FIELD);
    }

    private static JsonNode property(JsonNode node, String name) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            return node.get(name);
        }
        return SINGLE_VALUE_FIELD.equals(name) ? node : null;
    }

    private static boolean same(JsonNode before, JsonNode after) {
        return before == null ? after == null : before.equals(after);
    }

    private String text(Class<?> type, String name, JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value;
        try {
            value = node.isValueNode() ? node.asString() : objectMapper.writeValueAsString(node);
        } catch (RuntimeException e) {
            LOGGER.debug("渲染 {} 的 {} 字段失败: {}", type.getName(), name, e.getMessage());
            value = null;
        }
        return truncate(SensitiveFields.mask(type, name, value));
    }

    private String truncate(String value) {
        int max = reader.maxValueLength();
        if (value == null || max <= 0 || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private boolean ignored(String name) {
        List<String> ignoreFields = reader.ignoreFields();
        return CollectionUtils.isNotEmpty(ignoreFields) && ignoreFields.contains(name);
    }

    private static ObjectMapper rawValueMapper(ObjectMapper mapper) {
        ObjectMapper source = Objects.requireNonNullElseGet(mapper, () -> JsonMapper.builder().build());
        return source.rebuild()
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .build();
    }
}
