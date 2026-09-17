package cn.slainc.apikey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyGeneratorTest {

    @Test
    @DisplayName("默认生成 sk_ 前缀加 40 位字母数字")
    void defaultFormat() {
        ApiKeyGenerator generator = new ApiKeyGenerator("sk_", 40);
        for (int i = 0; i < 100; i++) {
            String key = generator.next();
            assertThat(key).startsWith("sk_");
            assertThat(key).hasSize(43);
            assertThat(key).matches("sk_[A-Za-z0-9]{40}");
        }
    }

    @Test
    @DisplayName("前缀与长度可配置")
    void customPrefixAndLength() {
        ApiKeyGenerator generator = new ApiKeyGenerator("ak-", 16);
        String key = generator.next();
        assertThat(key).startsWith("ak-");
        assertThat(key).hasSize(19);
        assertThat(key).matches("ak-[A-Za-z0-9]{16}");
    }

    @Test
    @DisplayName("空前缀时只输出随机段")
    void blankPrefixAllowed() {
        ApiKeyGenerator generator = new ApiKeyGenerator(null, 8);
        assertThat(generator.next()).matches("[A-Za-z0-9]{8}");
    }

    @Test
    @DisplayName("长度必须大于 0")
    void rejectInvalidLength() {
        assertThatThrownBy(() -> new ApiKeyGenerator("sk_", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("长度必须大于 0");
    }

    @Test
    @DisplayName("连续生成两千次不重复")
    void keysAreUnique() {
        ApiKeyGenerator generator = new ApiKeyGenerator("sk_", 40);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            seen.add(generator.next());
        }
        assertThat(seen).hasSize(2000);
    }
}
