package cn.slainc.search.config;

import cn.slainc.core.config.DynamicConfigService;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;

public class DynamicSearchProperties extends SearchProperties {

    public static final String KEY_ENABLED = "modality.search.enabled";

    public static final String KEY_DEFAULT_PAGE_SIZE = "modality.search.default-page-size";

    public static final String KEY_MAX_PAGE_SIZE = "modality.search.max-page-size";

    public static final String KEY_MAX_CONTENT_LENGTH = "modality.search.max-content-length";

    public static final String KEY_SNIPPET_LENGTH = "modality.search.snippet-length";

    public static final String KEY_MIN_TERM_LENGTH = "modality.search.min-term-length";

    public static final String KEY_STOP_WORDS = "modality.search.stop-words";

    public static final String KEY_K1 = "modality.search.k1";

    public static final String KEY_B = "modality.search.b";

    public static final String KEY_REINDEX_BATCH_SIZE = "modality.search.reindex-batch-size";

    private final SearchProperties fallback;

    private final ObjectProvider<DynamicConfigService> configService;

    public DynamicSearchProperties(SearchProperties fallback, ObjectProvider<DynamicConfigService> configService) {
        this.fallback = fallback == null ? new SearchProperties() : fallback;
        this.configService = configService;
    }

    @Override
    public boolean isEnabled() {
        DynamicConfigService service = service();
        if (service == null) {
            return fallback.isEnabled();
        }
        try {
            return service.getBoolean(KEY_ENABLED, fallback.isEnabled());
        } catch (RuntimeException e) {
            return fallback.isEnabled();
        }
    }

    @Override
    public int getDefaultPageSize() {
        DynamicConfigService service = service();
        if (service == null) {
            return fallback.getDefaultPageSize();
        }
        try {
            return service.getInt(KEY_DEFAULT_PAGE_SIZE, fallback.getDefaultPageSize());
        } catch (RuntimeException e) {
            return fallback.getDefaultPageSize();
        }
    }

    @Override
    public int getMaxPageSize() {
        DynamicConfigService service = service();
        if (service == null) {
            return fallback.getMaxPageSize();
        }
        try {
            return service.getInt(KEY_MAX_PAGE_SIZE, fallback.getMaxPageSize());
        } catch (RuntimeException e) {
            return fallback.getMaxPageSize();
        }
    }

    @Override
    public int getMaxContentLength() {
        DynamicConfigService service = service();
        if (service == null) {
            return fallback.getMaxContentLength();
        }
        try {
            return service.getInt(KEY_MAX_CONTENT_LENGTH, fallback.getMaxContentLength());
        } catch (RuntimeException e) {
            return fallback.getMaxContentLength();
        }
    }

    @Override
    public int getSnippetLength() {
        DynamicConfigService service = service();
        if (service == null) {
            return fallback.getSnippetLength();
        }
        try {
            return service.getInt(KEY_SNIPPET_LENGTH, fallback.getSnippetLength());
        } catch (RuntimeException e) {
            return fallback.getSnippetLength();
        }
    }

    @Override
    public int getMinTermLength() {
        DynamicConfigService service = service();
        if (service == null) {
            return fallback.getMinTermLength();
        }
        try {
            return service.getInt(KEY_MIN_TERM_LENGTH, fallback.getMinTermLength());
        } catch (RuntimeException e) {
            return fallback.getMinTermLength();
        }
    }

    @Override
    public List<String> getStopWords() {
        DynamicConfigService service = service();
        if (service == null) {
            return fallback.getStopWords();
        }
        try {
            String raw = service.getString(KEY_STOP_WORDS, null);
            if (raw == null || raw.isBlank()) {
                return fallback.getStopWords();
            }
            List<String> parsed = new ArrayList<>();
            for (String word : raw.split(",")) {
                String trimmed = word.trim();
                if (!trimmed.isEmpty()) {
                    parsed.add(trimmed);
                }
            }
            // 解析结果为空视为未配置，回落静态值
            return parsed.isEmpty() ? fallback.getStopWords() : List.copyOf(parsed);
        } catch (RuntimeException e) {
            return fallback.getStopWords();
        }
    }

    @Override
    public double getK1() {
        return decimal(KEY_K1, fallback.getK1());
    }

    @Override
    public double getB() {
        return decimal(KEY_B, fallback.getB());
    }

    @Override
    public int getReindexBatchSize() {
        DynamicConfigService service = service();
        if (service == null) {
            return fallback.getReindexBatchSize();
        }
        try {
            return service.getInt(KEY_REINDEX_BATCH_SIZE, fallback.getReindexBatchSize());
        } catch (RuntimeException e) {
            return fallback.getReindexBatchSize();
        }
    }

    private DynamicConfigService service() {
        return configService == null ? null : configService.getIfAvailable();
    }

    private double decimal(String key, double fallbackValue) {
        DynamicConfigService service = service();
        if (service == null) {
            return fallbackValue;
        }
        try {
            String value = service.findString(key).orElse(null);
            if (value == null || value.isBlank()) {
                return fallbackValue;
            }
            return Double.parseDouble(value.trim());
        } catch (RuntimeException e) {
            // 解析失败时回落静态值
            return fallbackValue;
        }
    }
}
