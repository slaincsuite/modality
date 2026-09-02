package cn.slainc.logger.mask;

import cn.slainc.logger.autoconfigure.LoggerProperties;
import cn.slainc.utils.json.JsonUtils;
import cn.slainc.web.log.SensitiveMasker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogMasker {

    private static final String KEY_VALUE_PATTERN =
            "(\\\"?)([\\p{L}_][\\p{L}\\p{N}_.\\-]*)(\\\"?)(\\s*[=:：＝]\\s*)(\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;&}\\]\\\"'{]+)";

    private final LoggerProperties.Mask properties;

    private final List<SensitiveFieldProvider> providers;

    private volatile Pattern keyValuePattern;

    private volatile List<CompiledRule> rules = List.of();

    private volatile Set<String> keywords = Set.of();

    private volatile Set<String> whitelist = Set.of();

    public LogMasker(LoggerProperties.Mask properties, List<SensitiveFieldProvider> providers) {
        this.properties = properties;
        this.providers = providers == null ? List.of() : new ArrayList<>(providers);
        this.providers.sort((left, right) -> Integer.compare(right.order(), left.order()));
        refresh(properties.getKeywords(), properties.getWhitelist(), properties.getRules());
    }

    public static LogMasker of(LoggerProperties properties) {
        return new LogMasker(properties.getMask(), List.of());
    }

    public void refresh(List<String> keywords, List<String> whitelist, List<MaskRule> rules) {
        Set<String> normalizedKeywords = new LinkedHashSet<>();
        if (keywords != null) {
            for (String keyword : keywords) {
                if (keyword != null && !keyword.isBlank()) {
                    normalizedKeywords.add(normalize(keyword));
                }
            }
        }
        this.keywords = Set.copyOf(normalizedKeywords);
        Set<String> normalizedWhitelist = new LinkedHashSet<>();
        if (whitelist != null) {
            for (String item : whitelist) {
                if (item != null && !item.isBlank()) {
                    normalizedWhitelist.add(normalize(item));
                }
            }
        }
        this.whitelist = Set.copyOf(normalizedWhitelist);
        List<CompiledRule> compiled = new ArrayList<>();
        if (rules != null) {
            for (MaskRule rule : rules) {
                if (rule == null || rule.pattern() == null || rule.pattern().isBlank()) {
                    continue;
                }
                compiled.add(new CompiledRule(Pattern.compile(rule.pattern()),
                        rule.replacement() == null ? properties.getReplacement() : rule.replacement()));
            }
        }
        this.rules = List.copyOf(compiled);
        this.keyValuePattern = Pattern.compile(KEY_VALUE_PATTERN);
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public boolean isSensitive(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        String normalized = normalize(fieldName);
        if (whitelist.contains(normalized)) {
            return false;
        }
        for (SensitiveFieldProvider provider : providers) {
            if (provider.isSensitive(fieldName)) {
                return true;
            }
        }
        if (SensitiveMasker.isSensitiveKey(fieldName)) {
            return true;
        }
        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public String maskValue(String fieldName, String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        for (SensitiveFieldProvider provider : providers) {
            if (provider.isSensitive(fieldName)) {
                String masked = provider.mask(fieldName, value);
                if (masked != null) {
                    return masked;
                }
            }
        }
        return properties.getReplacement();
    }

    public String maskMessage(String message) {
        if (message == null || message.isEmpty() || !properties.isEnabled()) {
            return message;
        }
        String trimmed = message.trim();
        if (properties.isMaskJsonMessage() && (trimmed.startsWith("{") || trimmed.startsWith("["))) {
            String masked = maskJson(message);
            if (masked != null) {
                return masked;
            }
        }
        return properties.isMaskTextMessage() ? maskText(message) : message;
    }

    public String maskJson(String json) {
        if (json == null || json.isBlank() || !properties.isEnabled()) {
            return json;
        }
        Object parsed;
        try {
            parsed = JsonUtils.mapper().readValue(json, Object.class);
        } catch (RuntimeException ex) {
            return properties.isMaskTextMessage() ? maskText(json) : json;
        }
        Object masked = maskObject(parsed);
        String reSerialized;
        try {
            reSerialized = JsonUtils.toJson(masked);
        } catch (RuntimeException ex) {
            return properties.isMaskTextMessage() ? maskText(json) : json;
        }
        return reSerialized == null ? json : applyRules(reSerialized);
    }

    public String maskText(String text) {
        if (text == null || text.isEmpty() || !properties.isEnabled()) {
            return text;
        }
        return applyRules(maskKeyValue(text));
    }

    private Object maskObject(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object item = entry.getValue();
                if (isSensitive(key)) {
                    result.put(key, item instanceof String text ? maskValue(key, text) : properties.getReplacement());
                } else {
                    result.put(key, maskObject(item));
                }
            }
            return result;
        }
        if (value instanceof List<?> source) {
            List<Object> result = new ArrayList<>(source.size());
            for (Object item : source) {
                result.add(maskObject(item));
            }
            return result;
        }
        if (value instanceof String text) {
            return properties.isMaskTextMessage() ? maskText(text) : text;
        }
        return value;
    }

    private String maskKeyValue(String text) {
        // 非敏感键的值可能吞掉内层 {"token":"x"}，因此逐轮收敛直到不再变化
        String current = text;
        for (int round = 0; round < 4; round++) {
            String next = maskKeyValueOnce(current);
            if (next.equals(current)) {
                return next;
            }
            current = next;
        }
        return current;
    }

    private String maskKeyValueOnce(String text) {
        Pattern pattern = keyValuePattern;
        if (pattern == null) {
            return text;
        }
        Matcher matcher = pattern.matcher(text);
        StringBuilder builder = new StringBuilder(text.length() + 16);
        while (matcher.find()) {
            String fieldName = matcher.group(2);
            if (!isSensitive(fieldName)) {
                continue;
            }
            String rawValue = matcher.group(5);
            String replacement = matcher.group(1) + fieldName + matcher.group(3) + matcher.group(4)
                    + quoteLike(rawValue, properties.getReplacement());
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private String quoteLike(String rawValue, String replacement) {
        if (rawValue.length() >= 2) {
            char first = rawValue.charAt(0);
            if ((first == '"' || first == '\'') && rawValue.charAt(rawValue.length() - 1) == first) {
                return first + replacement + first;
            }
        }
        return replacement;
    }

    private String applyRules(String text) {
        String result = text;
        for (CompiledRule rule : rules) {
            try {
                result = rule.pattern().matcher(result).replaceAll(rule.replacement());
            } catch (RuntimeException ignored) {
                // 单条规则失效不能影响日志输出
            }
        }
        return result;
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    private record CompiledRule(Pattern pattern, String replacement) {
    }
}
