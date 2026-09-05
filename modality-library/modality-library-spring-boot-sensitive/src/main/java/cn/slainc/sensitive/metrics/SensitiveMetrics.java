package cn.slainc.sensitive.metrics;

import cn.slainc.sensitive.annotation.SensitiveStrategy;
import cn.slainc.sensitive.annotation.SensitiveType;

public interface SensitiveMetrics {

    void recordMask(SensitiveType type, SensitiveStrategy strategy);

    default void recordSkip() {
    }

    static SensitiveMetrics noop() {
        return (type, strategy) -> {
        };
    }
}
