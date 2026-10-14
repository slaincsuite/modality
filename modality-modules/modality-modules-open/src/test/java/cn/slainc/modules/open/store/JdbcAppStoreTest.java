package cn.slainc.modules.open.store;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.jdbc.ClasspathSchemaInitializer;
import cn.slainc.openapi.domain.App;
import cn.slainc.openapi.domain.AppStatus;
import cn.slainc.openapi.domain.AppType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcAppStoreTest {

    private JdbcAppStore store;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:app-store-" + UUID.randomUUID().toString().replace("-", "")
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ClasspathSchemaInitializer(dataSource, "open-schema.sql").init();
        store = new JdbcAppStore(new JdbcTemplate(dataSource));
    }

    @Test
    void compareAndSetStatusOnlyWinsWhenExpectedStatusMatches() {
        App app = App.create("状态机应用", AppType.WEB, "dev-001");
        store.save(app);

        assertThat(store.compareAndSetStatus(withStatus(app.getAppId(), AppStatus.REVIEWING), AppStatus.DRAFT))
                .isTrue();
        assertThat(store.find(app.getAppId()).orElseThrow().getStatus()).isEqualTo(AppStatus.REVIEWING);

        assertThat(store.compareAndSetStatus(withStatus(app.getAppId(), AppStatus.ONLINE), AppStatus.DRAFT))
                .isFalse();
        assertThat(store.find(app.getAppId()).orElseThrow().getStatus()).isEqualTo(AppStatus.REVIEWING);

        store.delete(app.getAppId());
        assertThat(store.compareAndSetStatus(withStatus(app.getAppId(), AppStatus.DRAFT), AppStatus.REVIEWING))
                .isFalse();
    }

    @Test
    void duplicateInsertIsTranslatedToChineseConflict() {
        App app = App.create("重复应用", AppType.WEB, "dev-001");
        store.save(app);

        assertThatThrownBy(() -> store.insert(app))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已存在");
    }

    private App withStatus(String appId, AppStatus status) {
        App target = new App();
        target.setAppId(appId);
        target.setStatus(status);
        target.setUpdatedAt(System.currentTimeMillis());
        return target;
    }
}
