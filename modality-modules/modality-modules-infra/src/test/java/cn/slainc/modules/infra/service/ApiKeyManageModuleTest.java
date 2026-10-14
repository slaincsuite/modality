package cn.slainc.modules.infra.service;

import cn.slainc.modules.infra.InfraModuleTestBase;
import com.jayway.jsonpath.JsonPath;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;


// API 密钥管理全链路：签发 → 列表 → 启停 → 延期 → 轮换 → 删除
class ApiKeyManageModuleTest extends InfraModuleTestBase {

    private static long longOf(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    @Test
    void issueListDisableRotateDeleteLifecycle() throws Exception {
        var issued = post("/infra/api-keys/issue",
                "{\"loginId\":\"94001\",\"title\":\"验收密钥\",\"scopes\":[\"open\",\"pay\"],\"ttlDays\":30}");
        assertThat(issued).contains("\"isValid\":true");
        String apiKey = JsonPath.read(issued, "$.data.apiKey");
        assertThat(apiKey).isNotBlank();

        String list = send("GET", "/infra/api-keys?loginId=94001", null).body();
        assertThat(JsonPath.<Integer>read(list, "$.data.length()")).isEqualTo(1);

        assertThat(post("/infra/api-keys/" + apiKey + "/disable", null)).contains("DISABLED");
        assertThat(post("/infra/api-keys/" + apiKey + "/enable", null)).contains("ENABLED");

        String extended = post("/infra/api-keys/" + apiKey + "/extend", "{\"days\":30}");
        assertThat(JsonPath.<String>read(extended, "$.data.apiKey")).isEqualTo(apiKey);

        String rotated = post("/infra/api-keys/" + apiKey + "/rotate", "{\"graceHours\":24}");
        String freshKey = JsonPath.read(rotated, "$.data.apiKey");
        assertThat(freshKey).isNotEqualTo(apiKey);

        assertThat(send("DELETE", "/infra/api-keys/" + freshKey, null).statusCode()).isEqualTo(200);
        String afterDelete = send("GET", "/infra/api-keys?loginId=94001", null).body();
        assertThat(afterDelete).doesNotContain(freshKey);
    }

    @Test
    void csvExportListsKeysForLogin() throws Exception {
        post("/infra/api-keys/issue",
                "{\"loginId\":\"94002\",\"title\":\"CSV 密钥\",\"ttlDays\":30}");
        String csv = send("GET", "/infra/api-keys/94002/export.csv", null).body();
        assertThat(csv).contains("CSV 密钥").contains("94002");
    }

    @Test
    void pageQueriesAllKeysAcrossLogins() throws Exception {
        jdbc.execute("DELETE FROM t_infra_api_key");
        post("/infra/api-keys/issue",
                "{\"loginId\":\"94001\",\"title\":\"报表集成\",\"ttlDays\":30}");
        post("/infra/api-keys/issue",
                "{\"loginId\":\"94002\",\"title\":\"网关接入\",\"ttlDays\":30}");
        post("/infra/api-keys/issue",
                "{\"loginId\":\"94003\",\"title\":\"数据同步\",\"status\":\"ignored\"}");

        String all = send("GET", "/infra/api-keys/page?current=1&size=10", null).body();
        assertThat(longOf(JsonPath.read(all, "$.data.total"))).isEqualTo(3);

        String byKeyword = send("GET", "/infra/api-keys/page?keyword=网关", null).body();
        assertThat(longOf(JsonPath.read(byKeyword, "$.data.total"))).isEqualTo(1);
        assertThat(byKeyword).contains("网关接入");
    }

    @Test
    void issueRequiresLoginId() throws Exception {
        assertThat(post("/infra/api-keys/issue", "{\"title\":\"无主体\"}"))
                .contains("loginId 不能为空");
    }
}
