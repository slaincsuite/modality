package cn.slainc.sensitive.core;

import cn.slainc.crypto.digest.Sm3Utils;
import cn.slainc.crypto.key.CryptoKeyProvider;
import cn.slainc.sensitive.SensitiveException;
import cn.slainc.sensitive.annotation.Sensitive;
import cn.slainc.sensitive.annotation.SensitiveStrategy;
import cn.slainc.sensitive.annotation.SensitiveType;
import cn.slainc.sensitive.metrics.SensitiveMetrics;
import cn.slainc.sensitive.support.SensitiveTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

class SensitiveMaskerTest {

    private CryptoKeyProvider keyProvider;

    private SensitiveMasker masker;

    @BeforeEach
    void setUp() {
        keyProvider = SensitiveTestSupport.keyProvider();
        masker = SensitiveTestSupport.masker(keyProvider, SensitiveMetrics.noop());
    }

    @Test
    void hashesWithSm3FromCryptoModule() {
        assertEquals(Sm3Utils.digestHex("13812348888"), masker.hash("13812348888"));
        assertFalse(masker.hash("13812348888").contains("13812348888"));
    }

    @Test
    void encryptsWithCryptoKeyProviderAndDecryptsBack() {
        String encrypted = masker.encrypt("13812348888");
        assertTrue(encrypted.startsWith(SensitiveMasker.ENCRYPTED_PREFIX + "default:"), encrypted);
        assertFalse(encrypted.contains("13812348888"));
        assertEquals("13812348888", masker.decrypt(encrypted));
    }

    @Test
    void throwsChineseExceptionWhenKeyProviderMissing() {
        SensitiveMasker withoutKey = new SensitiveMasker(SensitiveConfig.defaults(), SensitiveMetrics.noop(),
                new SensitiveSwitch(true, () -> null), () -> null);
        SensitiveException encrypt = assertThrows(SensitiveException.class, () -> withoutKey.encrypt("13812348888"));
        assertTrue(encrypt.getMessage().contains("CryptoKeyProvider"), encrypt.getMessage());
        SensitiveException decrypt = assertThrows(SensitiveException.class,
                () -> withoutKey.decrypt("enc:v1:default:AAAA"));
        assertTrue(decrypt.getMessage().contains("CryptoKeyProvider"), decrypt.getMessage());
    }

    @Test
    void rejectsForeignCipherText() {
        SensitiveException exception = assertThrows(SensitiveException.class, () -> masker.decrypt("13812348888"));
        assertTrue(exception.getMessage().contains("前缀"), exception.getMessage());
        SensitiveException malformed = assertThrows(SensitiveException.class,
                () -> masker.decrypt("enc:v1:broken"));
        assertTrue(malformed.getMessage().contains("密文格式非法"), malformed.getMessage());
    }

    @Test
    void rejectsUnsupportedHashAlgorithm() {
        SensitiveMasker other = maskerWithConfig(new SensitiveConfig('*', "[已隐藏]", "MD5", true));
        SensitiveException exception = assertThrows(SensitiveException.class, () -> other.hash("x"));
        assertTrue(exception.getMessage().contains("MD5"), exception.getMessage());
    }

    @Test
    void usesConfiguredMaskCharWhenAnnotationDoesNotSpecify() {
        SensitiveMasker other = maskerWithConfig(new SensitiveConfig('#', "[已隐藏]", "SM3", true));
        SensitiveRule rule = other.rule(annotation("phone"));
        assertEquals("138####8888", other.mask("13812348888", rule));
    }

    @Test
    void usesConfiguredReplacementWhenAnnotationDoesNotSpecify() {
        SensitiveMasker other = maskerWithConfig(new SensitiveConfig('*', "***", "SM3", true));
        SensitiveRule rule = other.rule(annotation("replaced"));
        assertEquals("***", other.mask("任意值", rule));
    }

    @Test
    void masksEmailKeepingDomain() {
        assertEquals("z*******@example.com", masker.mask("zhangsan@example.com", masker.rule(annotation("email"))));
        assertEquals("a*@example.com", masker.mask("ab@example.com", masker.rule(annotation("email"))));
    }

    @Test
    void returnsRawWhenContextDisabled() {
        SensitiveRule rule = masker.rule(annotation("phone"));
        assertEquals("13812348888", SensitiveContext.callWithoutSensitive(() -> masker.mask("13812348888", rule)));
    }

    @Test
    void returnsRawWhenTextEmpty() {
        SensitiveRule rule = masker.rule(annotation("phone"));
        assertEquals("", masker.mask("", rule));
        assertNull(masker.mask(null, rule));
    }

    @Test
    void masksShortValueCompletely() {
        assertEquals("***", masker.mask("123", masker.rule(annotation("phone"))));
    }

    @Test
    void reportsInactiveForGroupedRuleWithoutActiveGroup() {
        SensitiveRule grouped = masker.rule(annotation("grouped"));
        assertFalse(masker.isActive(grouped));
        assertTrue(SensitiveContext.callWith(true, List.of("contact"), () -> masker.isActive(grouped)));
    }

    private SensitiveMasker maskerWithConfig(SensitiveConfig config) {
        return new SensitiveMasker(config, SensitiveMetrics.noop(), new SensitiveSwitch(true, () -> null),
                () -> keyProvider);
    }

    private Sensitive annotation(String fieldName) {
        try {
            return RuleFixture.class.getDeclaredField(fieldName).getAnnotation(Sensitive.class);
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static class RuleFixture {

        @Sensitive(type = SensitiveType.PHONE)
        private String phone;

        @Sensitive(type = SensitiveType.EMAIL)
        private String email;

        @Sensitive(strategy = SensitiveStrategy.REPLACE)
        private String replaced;

        @Sensitive(type = SensitiveType.PHONE, groups = "contact")
        private String grouped;
    }
}
