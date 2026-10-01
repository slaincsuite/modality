package cn.slainc.modules.open.config;

import cn.slainc.core.config.DynamicConfigService;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Arrays;
import java.util.List;

// 热配置读取：优先 DynamicConfigService（Redis 广播全节点生效），缺省回落到模块属性
public class OpenConfigReader {

    private static final String KEY_AUTO_APPROVE = "open.review.auto-approve";

    private static final String KEY_CATEGORIES = "open.app.categories";

    private final ObjectProvider<DynamicConfigService> configService;

    private final List<String> defaultCategories;

    public OpenConfigReader(ObjectProvider<DynamicConfigService> configService, List<String> defaultCategories) {
        this.configService = configService;
        this.defaultCategories = defaultCategories == null ? List.of() : List.copyOf(defaultCategories);
    }

    public boolean autoApprove() {
        return Boolean.parseBoolean(read(KEY_AUTO_APPROVE, "false"));
    }

    public List<String> categories() {
        String raw = read(KEY_CATEGORIES, String.join(",", defaultCategories));
        if (raw == null || raw.isBlank()) {
            return List.copyOf(defaultCategories);
        }
        return Arrays.stream(raw.split("[,，]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private String read(String key, String fallback) {
        DynamicConfigService service = configService == null ? null : configService.getIfAvailable();
        if (service == null) {
            return fallback;
        }
        return service.getString(key, fallback);
    }
}
