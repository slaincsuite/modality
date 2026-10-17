package cn.slainc.modules.auth.service.third;

import cn.slainc.core.exception.BizException;
import cn.slainc.crypto.key.MapCryptoKeyProvider;
import cn.slainc.crypto.key.SecretKeyUtils;
import cn.slainc.modules.auth.dto.FeishuConfigRequest;
import cn.slainc.modules.auth.store.FeishuAppConfigStore;
import cn.slainc.modules.auth.support.ThirdSecretCodec;
import cn.slainc.thirdauth.config.ThirdAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeishuAppConfigServiceTest {

    private FeishuAppConfigStore store;

    private ThirdAuthProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("feishuConfig" + System.nanoTime()).build();
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("SET MODE MySQL");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("auth-schema.sql"));
        }
        store = new FeishuAppConfigStore(new JdbcTemplate(dataSource));
        properties = new ThirdAuthProperties();
    }

    @Test
    void unreadableCipherIsReportedWithoutBreakingConfigView() {
        FeishuAppConfigService original = serviceWithNewKey();
        FeishuAppConfigService.AppConfigView saved = original.update(new FeishuConfigRequest(
                "cli_test", "secret-value", "verification-value", "encrypt-value",
                "http://localhost:8080/auth/third/callback/feishu"));
        assertThat(saved.secretReadable()).isTrue();
        assertThat(saved.configured()).isTrue();
        assertThat(saved.appSecret()).isNotBlank();

        FeishuAppConfigService.AppConfigView afterKeyChange = serviceWithNewKey().get();
        assertThat(afterKeyChange.secretReadable()).isFalse();
        assertThat(afterKeyChange.verificationTokenReadable()).isFalse();
        assertThat(afterKeyChange.encryptKeyReadable()).isFalse();
        assertThat(afterKeyChange.configured()).isFalse();
        assertThat(afterKeyChange.appId()).isEqualTo("cli_test");
        assertThat(afterKeyChange.appSecret()).isNotBlank();
        assertThatThrownBy(() -> serviceWithNewKey().changeStatus(true))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("App ID 与 App Secret");
    }

    private FeishuAppConfigService serviceWithNewKey() {
        String key = SecretKeyUtils.encodeBase64(SecretKeyUtils.sm4Key());
        ThirdSecretCodec codec = new ThirdSecretCodec(
                MapCryptoKeyProvider.of(Map.of("default", key), "default", "SM4"));
        return new FeishuAppConfigService(store, codec, properties);
    }
}
