package cn.slainc.modules.tenant.manage;

import cn.slainc.modules.tenant.TenantModuleApplication;
import cn.slainc.utils.json.JsonUtils;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 租户中心：开通、试用、冻结、激活、续期、注销与分页/配额查询（真实 Tomcat + H2，平台管理员视角）
@SpringBootTest(classes = TenantModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantManageModuleTest {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private String token;

    @BeforeEach
    void login() throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/tenant-test/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        token = JsonPath.read(response.body(), "$.data");
    }

    private HttpResponse<String> post(String path, Map<String, Object> body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("satoken", token)
                
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> action(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("satoken", token)
                
                .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("satoken", token)
                .GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, Map<String, Object> body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("satoken", token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("satoken", token)
                .DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private Long idOf(String json) {
        return Long.parseLong(((String) JsonPath.read(json, "$.data.id")).trim());
    }

    private long longOf(String json, String path) {
        return Long.parseLong(String.valueOf((Object) JsonPath.read(json, path)).trim());
    }

    private Map<String, Object> body(String tenantCode, String tenantName) {
        return Map.of("tenantCode", tenantCode, "tenantName", tenantName,
                "contactName", "联系人", "contactEmail", "ops@example.com");
    }

    @Test
    void createsTenantWithDefaults() throws Exception {
        HttpResponse<String> response = post("/tenant",
                Map.of("tenantCode", "acme", "tenantName", "ACME 公司", "expireAt", "2027-01-01T00:00:00"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((int) JsonPath.read(response.body(), "$.code")).isEqualTo(200);
        String createdId = JsonPath.read(response.body(), "$.data.id");
        assertThat(createdId).isNotBlank();
        assertThat((String) JsonPath.read(response.body(), "$.data.status")).isEqualTo("TRIAL");
        assertThat((String) JsonPath.read(response.body(), "$.data.isolationStrategy")).isEqualTo("ROW");
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM t_tenant WHERE tenant_code = 'acme'", Long.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateTenantCode() throws Exception {
        post("/tenant", body("dup", "第一家"));
        HttpResponse<String> response = post("/tenant", body("dup", "第二家"));

        assertThat((int) JsonPath.read(response.body(), "$.code")).isNotEqualTo(200);
    }

    @Test
    void trialFillsDefaultExpiry() throws Exception {
        HttpResponse<String> response = post("/tenant/trial", body("trial-co", "试用租户"));

        assertThat((int) JsonPath.read(response.body(), "$.code")).isEqualTo(200);
        assertThat((String) JsonPath.read(response.body(), "$.data.status")).isEqualTo("TRIAL");
        assertThat((String) JsonPath.read(response.body(), "$.data.expireAt")).isNotBlank();
    }

    @Test
    void freezesThenActivatesTenant() throws Exception {
        Long id = idOf(post("/tenant", body("frozen-co", "冻结对象")).body());

        HttpResponse<String> frozen = action("/tenant/" + id + "/freeze");
        assertThat((String) JsonPath.read(frozen.body(), "$.data.status")).isEqualTo("FROZEN");

        HttpResponse<String> activated = action("/tenant/" + id + "/activate");
        assertThat((String) JsonPath.read(activated.body(), "$.data.status")).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsRenewWithPastExpiry() throws Exception {
        Long id = idOf(post("/tenant", body("renew-co", "续期对象")).body());

        HttpResponse<String> response = post("/tenant/" + id + "/renew", Map.of("expireAt", "2020-01-01T00:00:00"));

        assertThat((int) JsonPath.read(response.body(), "$.code")).isNotEqualTo(200);
    }

    @Test
    void renewsAndNormalizesStatus() throws Exception {
        Long id = idOf(post("/tenant", body("renew2-co", "续期激活")).body());
        action("/tenant/" + id + "/expire");

        HttpResponse<String> response = post("/tenant/" + id + "/renew", Map.of("expireAt", "2028-06-01T00:00:00"));

        assertThat((String) JsonPath.read(response.body(), "$.data.status")).isEqualTo("ACTIVE");
        assertThat((String) JsonPath.read(response.body(), "$.data.expireAt")).startsWith("2028-06-01");
    }

    @Test
    void pagesByStatus() throws Exception {
        post("/tenant", body("page-a", "分页甲"));
        Long frozenId = idOf(post("/tenant", body("page-b", "分页乙")).body());
        action("/tenant/" + frozenId + "/freeze");

        HttpResponse<String> response = get("/tenant/page?status=FROZEN&current=1&size=10");

        assertThat((int) JsonPath.read(response.body(), "$.code")).isEqualTo(200);
        assertThat(longOf(response.body(), "$.data.total")).isEqualTo(1);
        assertThat((String) JsonPath.read(response.body(), "$.data.records[0].tenantCode")).isEqualTo("page-b");
    }

    @Test
    void queriesQuotaDimensions() throws Exception {
        Long id = idOf(post("/tenant", body("quota-co", "配额对象")).body());

        HttpResponse<String> response = get("/tenant/" + id + "/quota");

        assertThat((int) JsonPath.read(response.body(), "$.code")).isEqualTo(200);
        assertThat(longOf(response.body(), "$.data.tenantId")).isEqualTo(id);
        assertThat(String.valueOf((Object) JsonPath.read(response.body(), "$.data.maxUsers"))).isNotBlank();
        assertThat(String.valueOf((Object) JsonPath.read(response.body(), "$.data.maxStorage"))).isNotBlank();
        assertThat(String.valueOf((Object) JsonPath.read(response.body(), "$.data.maxApiQps"))).isNotBlank();
        assertThat(String.valueOf((Object) JsonPath.read(response.body(), "$.data.maxApiDaily"))).isNotBlank();
    }

    @Test
    void deletesTenantLogically() throws Exception {
        Long id = idOf(post("/tenant", body("gone-co", "注销对象")).body());

        HttpResponse<String> deleted = delete("/tenant/" + id);
        assertThat((int) JsonPath.read(deleted.body(), "$.code")).isEqualTo(200);

        HttpResponse<String> detail = get("/tenant/" + id);
        assertThat((int) JsonPath.read(detail.body(), "$.code")).isNotEqualTo(200);
        Long rows = jdbc.queryForObject("SELECT COUNT(*) FROM t_tenant WHERE id = ? AND deleted = 0", Long.class, id);
        assertThat(rows).isEqualTo(0);
    }

    @Test
    void updatesTenantProfile() throws Exception {
        Long id = idOf(post("/tenant", body("profile-co", "原名")).body());

        HttpResponse<String> response = put("/tenant/" + id,
                Map.of("tenantName", "新名称", "contactName", "新联系人", "remark", "已更新"));

        assertThat((int) JsonPath.read(response.body(), "$.code")).isEqualTo(200);
        assertThat((String) JsonPath.read(response.body(), "$.data.tenantName")).isEqualTo("新名称");
        assertThat((String) JsonPath.read(response.body(), "$.data.remark")).isEqualTo("已更新");
        assertThat((String) JsonPath.read(response.body(), "$.data.tenantCode")).isEqualTo("profile-co");
    }

    @Test
    void disabledTenantCannotBeReactivated() throws Exception {
        Long id = idOf(post("/tenant", body("off-co", "停用对象")).body());

        HttpResponse<String> disabled = action("/tenant/" + id + "/disable");
        assertThat((String) JsonPath.read(disabled.body(), "$.data.status")).isEqualTo("DISABLED");

        HttpResponse<String> activated = action("/tenant/" + id + "/activate");
        assertThat((int) JsonPath.read(activated.body(), "$.code")).isNotEqualTo(200);
    }

    @Test
    void adjustsQuotaThroughDynamicConfig() throws Exception {
        Long id = idOf(post("/tenant", body("adj-co", "配额调整")).body());

        HttpResponse<String> adjusted = put("/tenant/" + id + "/quota",
                Map.of("maxUsers", 50, "maxStorage", 1073741824L));

        assertThat((int) JsonPath.read(adjusted.body(), "$.code")).isEqualTo(200);
        assertThat(longOf(adjusted.body(), "$.data.maxUsers")).isEqualTo(50);
        assertThat(longOf(adjusted.body(), "$.data.maxStorage")).isEqualTo(1073741824L);

        HttpResponse<String> queried = get("/tenant/" + id + "/quota");
        assertThat(longOf(queried.body(), "$.data.maxUsers")).isEqualTo(50);
    }

    @Test
    void statsCountsByStatus() throws Exception {
        post("/tenant", body("stat-a", "统计甲"));
        Long frozenId = idOf(post("/tenant", body("stat-b", "统计乙")).body());
        action("/tenant/" + frozenId + "/freeze");

        HttpResponse<String> response = get("/tenant/stats");

        assertThat((int) JsonPath.read(response.body(), "$.code")).isEqualTo(200);
        assertThat(longOf(response.body(), "$.data.total")).isGreaterThanOrEqualTo(2);
        assertThat(longOf(response.body(), "$.data.FROZEN")).isGreaterThanOrEqualTo(1);
        assertThat(longOf(response.body(), "$.data.ACTIVE")).isGreaterThanOrEqualTo(0);
        assertThat(longOf(response.body(), "$.data.DISABLED")).isGreaterThanOrEqualTo(0);
    }

    @Test
    void rejectsInvalidIsolationStrategy() throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(body("bad-co", "非法策略"));
        body.put("isolationStrategy", "CLUSTER");

        HttpResponse<String> response = post("/tenant", body);

        assertThat((int) JsonPath.read(response.body(), "$.code")).isNotEqualTo(200);
    }
}
