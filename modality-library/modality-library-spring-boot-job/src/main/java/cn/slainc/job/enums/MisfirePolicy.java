package cn.slainc.job.enums;

import java.util.Locale;

public enum MisfirePolicy {

    SKIP,

    FIRE_ONCE_NOW;

    public static MisfirePolicy of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // 解析失败按无值处理，由调用方决定默认策略
            return null;
        }
    }

    public static MisfirePolicy of(String value, MisfirePolicy defaultValue) {
        MisfirePolicy policy = of(value);
        return policy == null ? defaultValue : policy;
    }
}
