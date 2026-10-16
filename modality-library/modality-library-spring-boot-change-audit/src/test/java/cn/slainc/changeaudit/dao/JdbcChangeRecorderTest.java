package cn.slainc.changeaudit.dao;

import cn.slainc.changeaudit.spi.ChangeRecord;
import cn.slainc.core.jdbc.ClasspathSchemaInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcChangeRecorderTest {

    private static JdbcTemplate jdbcTemplate;

    private static JdbcChangeRecorder recorder;

    @BeforeAll
    static void setUp() throws Exception {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(new org.h2.Driver(),
                "jdbc:h2:mem:change-audit-recorder;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        new ClasspathSchemaInitializer(dataSource, "change-audit-schema.sql").init();
        recorder = new JdbcChangeRecorder(jdbcTemplate, "t_change_audit_record");
    }

    @Test
    @DisplayName("变更记录整行落库，中文与 JSON 差异原样保留")
    void recordPersistsEveryColumn() {
        LocalDateTime changeTime = LocalDateTime.of(2026, 10, 16, 10, 30, 0);
        recorder.record(new ChangeRecord("TENANT_USER", "7001", "张三", "UPDATE",
                "[{\"field\":\"nickname\",\"before\":\"旧名\",\"after\":\"新名\"}]", 1, "admin", 2001L,
                "/upms/user/7001", "trace-7001", "API", changeTime, 9L));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM t_change_audit_record WHERE entity_id = ?", "7001");
        assertEquals("TENANT_USER", row.get("entity_type"));
        assertEquals("张三", row.get("entity_name"));
        assertEquals("UPDATE", row.get("operation"));
        assertEquals(1, row.get("changed_fields"));
        assertTrue(String.valueOf(row.get("changes")).contains("旧名"));
        assertTrue(String.valueOf(row.get("changes")).contains("新名"));
        assertEquals("admin", row.get("operator"));
        assertEquals(2001L, row.get("operator_id"));
        assertEquals("/upms/user/7001", row.get("request_uri"));
        assertEquals("trace-7001", row.get("trace_id"));
        assertEquals("API", row.get("change_source"));
        assertEquals(9L, row.get("tenant_id"));
        assertEquals(changeTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                row.get("change_time"));
        assertNotNull(row.get("create_time"));
    }

    @Test
    @DisplayName("缺少时间与归属时仍落库，时间兜底为当前时刻")
    void recordToleratesMissingOptionalValues() {
        recorder.record(new ChangeRecord("TENANT_USER", "7002", null, "DELETE", "[]", 0, null, null, null, null,
                "SYSTEM", null, null));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM t_change_audit_record WHERE entity_id = ?", "7002");
        assertEquals(0, row.get("changed_fields"));
        assertNotNull(row.get("change_time"));
        assertNotNull(row.get("create_time"));
    }

    @Test
    @DisplayName("落库失败不抛异常，业务改动不被审计反噬")
    void writeFailureIsSwallowed() {
        JdbcChangeRecorder broken = new JdbcChangeRecorder(jdbcTemplate, "t_change_audit_missing");
        assertDoesNotThrow(() -> broken.record(new ChangeRecord("TENANT_USER", "7003", null, "UPDATE", "[]", 0,
                null, null, null, null, "SYSTEM", LocalDateTime.now(), null)));
    }
}
