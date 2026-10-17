package cn.slainc.modules.infra.service.apikey;

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

    @Test
    void detailPageStatsAndRotationHistoryAreAvailable() throws Exception {
        String issued = post("/infra/api-keys/issue",
                "{\"loginId\":\"94011\",\"title\":\"报表读取\",\"intro\":\"供报表系统只读调用\","
                        + "\"scopes\":[\"report:read\"],\"ttlDays\":30}");
        String apiKey = JsonPath.read(issued, "$.data.apiKey");
        assertThat((String) JsonPath.read(issued, "$.data.intro")).isEqualTo("供报表系统只读调用");

        String detail = get("/infra/api-keys/" + apiKey);
        assertThat((String) JsonPath.read(detail, "$.data.apiKey")).isEqualTo(apiKey);
        assertThat((String) JsonPath.read(detail, "$.data.intro")).isEqualTo("供报表系统只读调用");

        String page = get("/infra/api-keys/page?current=1&size=5&keyword=报表");
        assertThat(longOf(JsonPath.read(page, "$.data.current"))).isEqualTo(1);
        assertThat(longOf(JsonPath.read(page, "$.data.size"))).isEqualTo(5);
        assertThat(longOf(JsonPath.read(page, "$.data.pages"))).isEqualTo(1);
        assertThat(page).contains("\"lastUsedTime\"").contains("last_used_time");

        String rotated = post("/infra/api-keys/" + apiKey + "/rotate", "{\"graceHours\":24}");
        String freshKey = JsonPath.read(rotated, "$.data.apiKey");
        String rotations = get("/infra/api-keys/" + apiKey + "/rotations");
        assertThat((Integer) JsonPath.read(rotations, "$.data.length()")).isEqualTo(1);
        assertThat((String) JsonPath.read(rotations, "$.data[0].newApiKey")).isEqualTo(freshKey);
        assertThat((String) JsonPath.read(rotations, "$.data[0].description")).contains("24 小时过渡期");

        String stats = get("/infra/api-keys/" + freshKey + "/stats");
        assertThat(longOf(JsonPath.read(stats, "$.data.total"))).isZero();
        assertThat(longOf(JsonPath.read(stats, "$.data.failureCount"))).isZero();
        assertThat((Boolean) JsonPath.read(stats, "$.data.detailedMetricsAvailable")).isTrue();
    }

    @Test
    void statsCaptureFailuresAverageElapsedAndLastUsedTime() throws Exception {
        String issued = post("/infra/api-keys/issue",
                "{\"loginId\":\"94012\",\"title\":\"探针密钥\",\"scopes\":[\"probe:read\"],\"ttlDays\":30}");
        String apiKey = JsonPath.read(issued, "$.data.apiKey");

        assertThat(sendWithApiKey(apiKey, "GET", "/infra-test/login", null).statusCode()).isEqualTo(200);
        assertThat(sendWithApiKey(apiKey, "GET", "/missing-api-key-metrics-probe", null).statusCode())
                .isGreaterThanOrEqualTo(400);

        String stats = get("/infra/api-keys/" + apiKey + "/stats");
        assertThat(longOf(JsonPath.read(stats, "$.data.total"))).isEqualTo(2);
        assertThat(longOf(JsonPath.read(stats, "$.data.failureCount"))).isEqualTo(1);
        assertThat(((Number) JsonPath.read(stats, "$.data.averageElapsedMillis")).doubleValue())
                .isGreaterThanOrEqualTo(0D);
        assertThat((Object) JsonPath.read(stats, "$.data.lastUsedTime")).isNotNull();

        String detail = get("/infra/api-keys/" + apiKey);
        assertThat((Object) JsonPath.read(detail, "$.data.lastUsedTime")).isNotNull();
        String page = get("/infra/api-keys/page?keyword=探针密钥");
        assertThat(page).doesNotContain("\"lastUsedTime\":null");
    }

    @Test
    void userScopedApiKeyEndpointsIssueListAndManageOwnedKeys() throws Exception {
        var created = send("POST", "/upms/user/95001/api-keys",
                "{\"title\":\"用户自助密钥\",\"description\":\"个人脚本调用\",\"scopes\":[\"self:read\"]}");
        assertThat(created.statusCode()).isEqualTo(200);
        String apiKey = JsonPath.read(created.body(), "$.data.apiKey");
        assertThat((String) JsonPath.read(created.body(), "$.data.intro")).isEqualTo("个人脚本调用");

        String list = get("/upms/user/95001/api-keys");
        assertThat((Integer) JsonPath.read(list, "$.data.length()")).isEqualTo(1);
        assertThat((String) JsonPath.read(list, "$.data[0].apiKey")).isEqualTo(apiKey);

        var updated = send("PUT", "/upms/user/95001/api-keys",
                "{\"apiKey\":\"" + apiKey + "\",\"title\":\"脚本密钥\",\"intro\":\"更新后的说明\","
                        + "\"enabled\":false}");
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(updated.body(), "$.data.title")).isEqualTo("脚本密钥");
        assertThat((String) JsonPath.read(updated.body(), "$.data.status")).isEqualTo("disabled");

        var foreign = send("PUT", "/upms/user/95002/api-keys",
                "{\"apiKey\":\"" + apiKey + "\",\"title\":\"越权修改\"}");
        assertThat(foreign.statusCode()).isEqualTo(400);
        assertThat(foreign.body()).contains("不属于指定用户");
    }
}
