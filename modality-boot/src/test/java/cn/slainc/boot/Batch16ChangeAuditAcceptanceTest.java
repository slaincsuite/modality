package cn.slainc.boot;

import cn.slainc.changeaudit.annotation.ChangeAudited;
import cn.slainc.changeaudit.spi.SnapshotLoader;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

// 批次 16 整机验收：@ChangeAudited 采集 → 字段差异 → 提交后 JDBC 落库（真实 H2 + 真实 Redis）
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class Batch16ChangeAuditAcceptanceTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SampleService sampleService;

    @Autowired
    private SnapshotHolder snapshotHolder;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        UserContext.set(UserContext.UserInfo.of(2001L, "batch16", null));
        jdbc.execute("DELETE FROM t_change_audit_record");
        snapshotHolder.clear();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        TenantContext.clear();
    }

    @Test
    @DisplayName("字段真的变化时按平台表落库，含差异 JSON、操作人与租户")
    void changedFieldsLandInDatabase() {
        snapshotHolder.put(new Sample(7001L, "旧名称"));
        sampleService.update(new Sample(7001L, "新名称"));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM t_change_audit_record WHERE entity_id = '7001'");
        assertThat(row.get("entity_type")).isEqualTo("BATCH16_SAMPLE");
        assertThat(row.get("entity_name")).isEqualTo("新名称");
        assertThat(row.get("operation")).isEqualTo("UPDATE");
        assertThat(row.get("changed_fields")).isEqualTo(1);
        assertThat(String.valueOf(row.get("changes"))).contains("旧名称").contains("新名称");
        assertThat(row.get("operator")).isEqualTo("batch16");
        assertThat(row.get("operator_id")).isEqualTo(2001L);
        assertThat(row.get("tenant_id")).isEqualTo(1L);
        assertThat((Long) row.get("change_time")).isPositive();
        assertThat((Long) row.get("create_time")).isPositive();
    }

    @Test
    @DisplayName("字段没有变化时不产生记录")
    void unchangedSnapshotWritesNothing() {
        snapshotHolder.put(new Sample(7002L, "同名"));
        sampleService.update(new Sample(7002L, "同名"));

        assertThat(countOf("7002")).isZero();
    }

    @Test
    @DisplayName("平台表不受租户条件改写，任意租户上下文都能落库")
    void platformTableIsNotTenantFiltered() {
        TenantContext.set(9L);
        snapshotHolder.put(new Sample(7003L, "旧"));
        sampleService.update(new Sample(7003L, "新"));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM t_change_audit_record WHERE entity_id = '7003'");
        assertThat(row.get("tenant_id")).isEqualTo(9L);
    }

    private Integer countOf(String entityId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_change_audit_record WHERE entity_id = ?", Integer.class,
                entityId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ChangeAuditTestConfiguration {

        @Bean
        SnapshotHolder batch16SnapshotHolder() {
            return new SnapshotHolder();
        }

        @Bean
        SnapshotLoader batch16SnapshotLoader(SnapshotHolder holder) {
            return new SnapshotLoader() {

                @Override
                public String type() {
                    return "BATCH16_SAMPLE";
                }

                @Override
                public Object load(String id) {
                    return holder.take();
                }
            };
        }

        @Bean
        SampleService batch16SampleService() {
            return new SampleService();
        }
    }

    static class SnapshotHolder {

        private final AtomicReference<Object> snapshot = new AtomicReference<>();

        void put(Object value) {
            snapshot.set(value);
        }

        Object take() {
            return snapshot.getAndSet(null);
        }

        void clear() {
            snapshot.set(null);
        }
    }

    static class Sample {

        private Long id;

        private String name;

        Sample(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    static class SampleService {

        @ChangeAudited(type = "BATCH16_SAMPLE", id = "#entity.id", name = "#entity.name")
        public Sample update(Sample entity) {
            return entity;
        }
    }
}
