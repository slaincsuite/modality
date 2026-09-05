package cn.slainc.sensitive.core;

import cn.slainc.core.config.DynamicConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class SensitiveSwitch {

    public static final String DYNAMIC_KEY = "modality.sensitive.enabled";

    private static final Logger log = LoggerFactory.getLogger(SensitiveSwitch.class);

    private final boolean defaultEnabled;

    private final Supplier<DynamicConfigService> dynamicConfig;

    public SensitiveSwitch(boolean defaultEnabled, Supplier<DynamicConfigService> dynamicConfig) {
        this.defaultEnabled = defaultEnabled;
        this.dynamicConfig = dynamicConfig;
    }

    public boolean isEnabled() {
        if (!SensitiveContext.isEnabled()) {
            return false;
        }
        if (dynamicConfig == null) {
            return defaultEnabled;
        }
        try {
            DynamicConfigService service = dynamicConfig.get();
            return service == null ? defaultEnabled : service.getBoolean(DYNAMIC_KEY, defaultEnabled);
        } catch (RuntimeException exception) {
            // 动态配置读取失败时必须回退到默认值并继续脱敏，否则响应序列化会被基础设施故障打断
            log.warn("读取动态配置 {} 失败，脱敏开关回退为 {}", DYNAMIC_KEY, defaultEnabled, exception);
            return defaultEnabled;
        }
    }
}
