package cn.slainc.modules.auth.service.third;

import cn.slainc.core.exception.BizException;
import cn.slainc.modules.auth.store.FeishuAppConfigStore;
import cn.slainc.modules.auth.store.FeishuSyncRecordStore;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeishuContactSyncServiceTest {

    private JdbcTemplate jdbcTemplate;

    private FeishuSyncRecordStore syncRecordStore;

    private FeishuContactSyncService syncService;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("feishuSync" + System.nanoTime()).build();
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("SET MODE MySQL");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("auth-schema.sql"));
        }
        jdbcTemplate = new JdbcTemplate(dataSource);
        syncRecordStore = new FeishuSyncRecordStore(jdbcTemplate);
        ThirdAuthProperties properties = new ThirdAuthProperties();
        FeishuAppConfigService configService = new FeishuAppConfigService(
                new FeishuAppConfigStore(jdbcTemplate), new ThirdSecretCodec(null), properties);
        syncService = new FeishuContactSyncService(configService, syncRecordStore, properties);
    }

    @Test
    void missingCredentialsFailWithChineseMessageAndLeaveFailedRecord() {
        assertThatThrownBy(() -> syncService.sync("tester"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请先配置 App ID 与 App Secret");

        assertThat(syncRecordStore.count("FAILED")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT message FROM t_auth_third_sync_record WHERE status = 'FAILED'", String.class))
                .contains("App ID 与 App Secret");
    }
}
