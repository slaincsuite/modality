package cn.slainc.mfa.recovery;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecoveryCodeGeneratorTest {

    private final RecoveryCodeGenerator generator = new RecoveryCodeGenerator();

    @Test
    void shouldGenerateTheRequestedAmountOfFormattedCodes() {
        List<String> codes = generator.generate(10);

        assertThat(codes).hasSize(10).doesNotHaveDuplicates();
        assertThat(codes).allMatch(code -> code.matches("[A-Z2-9]{4}-[A-Z2-9]{4}"));
    }

    @Test
    void shouldGenerateNothingForANonPositiveCount() {
        assertThat(generator.generate(0)).isEmpty();
        assertThat(generator.generate(-1)).isEmpty();
    }

    @Test
    void shouldHashCodesWithSha256() {
        String code = generator.generate(1).get(0);
        String hash = generator.hash(code);

        assertThat(hash).hasSize(64).doesNotContain(code).doesNotContain(code.replace("-", ""));
        assertThat(hash).isEqualTo(generator.hash(code));
        assertThat(generator.hash(code.toLowerCase(Locale.ROOT).replace("-", ""))).isEqualTo(hash);
    }

    @Test
    void shouldMatchCodesIgnoringCaseAndSeparators() {
        String code = generator.generate(1).get(0);
        String hash = generator.hash(code);

        assertThat(generator.matches(code, hash)).isTrue();
        assertThat(generator.matches(code.toLowerCase(Locale.ROOT), hash)).isTrue();
        assertThat(generator.matches(code.replace("-", ""), hash)).isTrue();
        assertThat(generator.matches(" " + code + " ", hash)).isTrue();
        assertThat(generator.matches("AAAA-AAAA", hash)).isFalse();
        assertThat(generator.matches(null, hash)).isFalse();
        assertThat(generator.matches(code, null)).isFalse();
    }

    @Test
    void shouldRejectBlankCodesWhenHashing() {
        assertThatThrownBy(() -> generator.hash(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.hash("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
