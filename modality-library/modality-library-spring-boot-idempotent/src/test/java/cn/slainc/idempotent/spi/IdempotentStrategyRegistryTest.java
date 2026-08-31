package cn.slainc.idempotent.spi;

import cn.slainc.idempotent.annotation.IdempotentStrategy;
import cn.slainc.idempotent.key.ParameterFingerprint;
import cn.slainc.idempotent.support.IdempotentContext;
import cn.slainc.idempotent.support.IdempotentSettings;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotentStrategyRegistryTest {

    private final IdempotentStrategyRegistry registry = new IdempotentStrategyRegistry(List.of(
            new ParameterStrategyHandler(new ParameterFingerprint(), "modality:idempotent:"),
            new TokenStrategyHandler("modality:idempotent:", true),
            new HybridStrategyHandler(new ParameterFingerprint(), "modality:idempotent:", true)));

    @Test
    void 内置策略按枚举解析() {
        assertThat(registry.resolve(settings(IdempotentStrategy.PARAMETER, null)).name()).isEqualTo("parameter");
        assertThat(registry.resolve(settings(IdempotentStrategy.TOKEN, null)).name()).isEqualTo("token");
        assertThat(registry.resolve(settings(IdempotentStrategy.HYBRID, null)).name()).isEqualTo("hybrid");
    }

    @Test
    void 自定义策略按名称解析并且后注册覆盖先注册() {
        registry.register(customHandler("my-custom"));
        assertThat(registry.resolve(settings(IdempotentStrategy.CUSTOM, "my-custom")).name()).isEqualTo("my-custom");
        registry.register(customHandler("my-custom"));
        assertThat(registry.find("my-custom")).isPresent();
    }

    @Test
    void 自定义策略必须指定处理器名称() {
        assertThatThrownBy(() -> registry.resolve(settings(IdempotentStrategy.CUSTOM, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须通过 @Idempotent(handler");
    }

    @Test
    void 未注册的处理器给出中文提示() {
        assertThatThrownBy(() -> registry.resolve(settings(IdempotentStrategy.CUSTOM, "not-exists")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未找到自定义幂等策略处理器：not-exists");
    }

    @Test
    void ServiceLoader可以加载外部扩展() {
        IdempotentStrategyRegistry spiRegistry = new IdempotentStrategyRegistry(List.of());
        assertThat(spiRegistry.find("test-spi")).as("通过 META-INF/services 加载的扩展").isPresent();
        IdempotentStrategyRegistry withoutSpi = new IdempotentStrategyRegistry(List.of(), false);
        assertThat(withoutSpi.find("test-spi")).isEmpty();
    }

    private static IdempotentStrategyHandler customHandler(String name) {
        return new IdempotentStrategyHandler() {

            @Override
            public String name() {
                return name;
            }

            @Override
            public IdempotentKey resolve(IdempotentContext context) {
                return new IdempotentKey("custom:" + context.methodId(), null, null);
            }
        };
    }

    private static IdempotentSettings settings(IdempotentStrategy strategy, String handler) {
        return new IdempotentSettings(true, strategy, handler, "请勿重复提交", Duration.ofMinutes(5), 3,
                Duration.ofMinutes(5), List.of(), true, true, true, true, true);
    }
}
