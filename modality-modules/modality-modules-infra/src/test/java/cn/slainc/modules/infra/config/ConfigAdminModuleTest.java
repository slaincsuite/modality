package cn.slainc.modules.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

// 动态配置管理台：CRUD、加密拒止（无服务路线）、历史回滚、导入导出（无 Redis 路线，走 JdbcTemplate 回退写入）
class ConfigAdminModuleTest extends cn.slainc.modules.infra.InfraModuleTestBase {

    @Test
    void crudPageSearchAndDetail() throws Exception {
        post("/infra/config", "{\"key\":\"infra.test.timeout\",\"value\":\"30\",\"group\":\"验收\","
                + "\"description\":\"超时秒数\"}");
        post("/infra/config", "{\"key\":\"infra.test.name\",\"value\":\"模块测试\",\"group\":\"其它\"}");

        String page = get("/infra/config/page?keyword=infra.test");
        assertThat(Integer.parseInt(JsonPath.read(page, "$.data.total").toString())).isEqualTo(2);

        String groupPage = get("/infra/config/page?group=验收");
        assertThat(Integer.parseInt(JsonPath.read(groupPage, "$.data.total").toString())).isEqualTo(1);
        assertThat(groupPage).contains("infra.test.timeout");

        String groups = get("/infra/config/groups");
        assertThat(groups).contains("验收").contains("其它");

        String detail = get("/infra/config/infra.test.timeout");
        assertThat(JsonPath.read(detail, "$.data.valuePreview").toString()).isEqualTo("30");

        send("PUT", "/infra/config/infra.test.timeout", "{\"value\":\"60\",\"description\":\"超时秒数（改）\"}");
        assertThat(JsonPath.read(get("/infra/config/infra.test.timeout"), "$.data.valuePreview").toString())
                .isEqualTo("60");
        String description = jdbc.queryForObject(
                "SELECT description FROM t_dynamic_config WHERE config_key = 'infra.test.timeout'", String.class);
        assertThat(description).isEqualTo("超时秒数（改）");

        send("DELETE", "/infra/config/infra.test.name", null);
        HttpResponse<String> missing = send("GET", "/infra/config/infra.test.name", null);
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(missing.body()).contains("动态配置不存在");
    }

    @Test
    void encryptedConfigRejectedWithoutDynamicConfigService() throws Exception {
        HttpResponse<String> response = send("POST", "/infra/config",
                "{\"key\":\"infra.test.secret\",\"value\":\"s3cret\",\"encrypted\":true}");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("未启用动态配置服务");
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_dynamic_config WHERE config_key = 'infra.test.secret'", Integer.class);
        assertThat(rows).isZero();
    }

    @Test
    void updateHistoryAndRollbackRestoresPreviousValue() throws Exception {
        post("/infra/config", "{\"key\":\"infra.test.rollback\",\"value\":\"v1\",\"group\":\"验收\"}");
        send("PUT", "/infra/config/infra.test.rollback", "{\"value\":\"v2\"}");
        send("PUT", "/infra/config/infra.test.rollback", "{\"value\":\"v3\"}");

        String history = get("/infra/config/history/page?key=infra.test.rollback");
        assertThat(Integer.parseInt(JsonPath.read(history, "$.data.total").toString())).isEqualTo(3);
        assertThat(history).contains("CREATE").contains("UPDATE");

        String secondHistoryId = JsonPath.read(
                get("/infra/config/history/page?key=infra.test.rollback&current=1&size=10"),
                "$.data.records[1].id").toString();
        post("/infra/config/history/" + secondHistoryId + "/rollback", null);
        assertThat(JsonPath.read(get("/infra/config/infra.test.rollback"), "$.data.valuePreview").toString())
                .isEqualTo("v1");
        assertThat(get("/infra/config/history/page?key=infra.test.rollback")).contains("ROLLBACK");
    }

    @Test
    void rollbackOfDeleteRestoresConfig() throws Exception {
        post("/infra/config", "{\"key\":\"infra.test.deleted\",\"value\":\"keep\",\"group\":\"验收\"}");
        send("DELETE", "/infra/config/infra.test.deleted", null);
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_dynamic_config WHERE config_key = 'infra.test.deleted' AND deleted = 0",
                Integer.class);
        assertThat(active).isZero();

        String deleteHistoryId = JsonPath.read(
                get("/infra/config/history/page?key=infra.test.deleted"), "$.data.records[0].id").toString();
        post("/infra/config/history/" + deleteHistoryId + "/rollback", null);
        assertThat(JsonPath.read(get("/infra/config/infra.test.deleted"), "$.data.valuePreview").toString())
                .isEqualTo("keep");
    }

    @Test
    void historyValuesEncryptedAtRest() throws Exception {
        post("/infra/config", "{\"key\":\"infra.test.cipher\",\"value\":\"plain-secret-value\",\"group\":\"验收\"}");
        String stored = jdbc.queryForObject(
                "SELECT new_value FROM t_infra_config_history WHERE config_key = 'infra.test.cipher'"
                        + " AND operation = 'CREATE'", String.class);
        assertThat(stored).startsWith("ENC:");
        assertThat(stored).doesNotContain("plain-secret-value");
    }

    @Test
    void importAndExportRoundTrip() throws Exception {
        post("/infra/config", "{\"key\":\"infra.test.export\",\"value\":\"v1\",\"group\":\"验收\"}");
        String exported = raw("GET", "/infra/config/export?group=验收", null);
        assertThat(exported).startsWith("{\"items\":");
        assertThat(exported).contains("infra.test.export").contains("\"value\":\"v1\"");

        String summary = post("/infra/config/import", "{\"items\":[{\"key\":\"infra.test.imported\","
                + "\"value\":\"导入值\",\"group\":\"验收\",\"description\":\"导入\"},{\"key\":\"bad key!\","
                + "\"value\":\"x\"}]}");
        assertThat(Integer.parseInt(JsonPath.read(summary, "$.data.succeeded").toString())).isEqualTo(1);
        assertThat(Integer.parseInt(JsonPath.read(summary, "$.data.failed").toString())).isEqualTo(1);
        assertThat(JsonPath.read(get("/infra/config/infra.test.imported"), "$.data.valuePreview").toString())
                .isEqualTo("导入值");
    }
}
