package cn.slainc.modules.tenant.manage;

import cn.slainc.core.config.DynamicConfigService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 租户套餐：模板 CRUD、默认菜单范围、租户绑定 / 解绑与生效值回落（真实 Tomcat + H2，平台管理员视角）
@SpringBootTest(classes = TenantModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantPlanModuleTest {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DynamicConfigService configService;

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

    private HttpResponse<String> request(String method, String path, Map<String, Object> body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("satoken", token);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(body)));
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return request("GET", path, null);
    }

    private HttpResponse<String> post(String path, Map<String, Object> body) throws Exception {
        return request("POST", path, body);
    }

    private HttpResponse<String> put(String path, Map<String, Object> body) throws Exception {
        return request("PUT", path, body);
    }

    private HttpResponse<String> delete(String path) throws Exception {
        return request("DELETE", path, null);
    }

    private long idOf(String json) {
        return Long.parseLong(String.valueOf((Object) JsonPath.read(json, "$.data.id")).trim());
    }

    private long longOf(String json, String path) {
        return Long.parseLong(String.valueOf((Object) JsonPath.read(json, path)).trim());
    }

    private long createPlan(String code) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("planCode", code);
        body.put("planName", "标准版");
        body.put("maxUsers", 10);
        body.put("storageQuotaMb", 10240L);
        body.put("maxFileSizeMb", 20L);
        body.put("maxExportRows", 2000);
        body.put("features", List.of("SEARCH", "EXPORT"));
        body.put("dataScope", "SELF");
        HttpResponse<String> response = post("/tenant/plan", body);
        assertThat((int) JsonPath.read(response.body(), "$.code")).isEqualTo(200);
        return idOf(response.body());
    }

    private long createTenant(String code) throws Exception {
        HttpResponse<String> response = post("/tenant",
                Map.of("tenantCode", code, "tenantName", "套餐租户" + code));
        assertThat((int) JsonPath.read(response.body(), "$.code")).isEqualTo(200);
        return idOf(response.body());
    }

    private long bindPlan(long tenantId, Long planId) throws Exception {
        HttpResponse<String> response = put("/tenant/" + tenantId + "/plan",
                planId == null ? Map.of() : Map.of("planId", planId));
        assertThat((int) JsonPath.read(response.body(), "$.code")).isEqualTo(200);
        return longOf(response.body(), "$.data.planId");
    }

    @Test
    void createsAndQueriesPlans() throws Exception {
        long id = createPlan("std-" + System.currentTimeMillis());

        HttpResponse<String> page = get("/tenant/plan/page?keyword=std-&current=1&size=10");
        assertThat((int) JsonPath.read(page.body(), "$.code")).isEqualTo(200);
        assertThat(longOf(page.body(), "$.data.total")).isGreaterThanOrEqualTo(1);

        HttpResponse<String> list = get("/tenant/plan/list");
        assertThat((int) JsonPath.read(list.body(), "$.code")).isEqualTo(200);
        assertThat(longOf(list.body(), "$.data.length()")).isGreaterThanOrEqualTo(1);

        HttpResponse<String> detail = get("/tenant/plan/" + id);
        assertThat((String) JsonPath.read(detail.body(), "$.data.planCode")).startsWith("std-");
        assertThat((String) JsonPath.read(detail.body(), "$.data.dataScope")).isEqualTo("SELF");
        assertThat((int) JsonPath.read(detail.body(), "$.data.maxUsers")).isEqualTo(10);
        assertThat((boolean) JsonPath.read(detail.body(), "$.data.enabled")).isTrue();

        String features = jdbc.queryForObject("SELECT features FROM t_tenant_plan WHERE id = ?", String.class, id);
        assertThat(features).contains("SEARCH").contains("EXPORT");
    }

    @Test
    void rejectsDuplicatePlanCode() throws Exception {
        String code = "dup-" + System.currentTimeMillis();
        createPlan(code);

        Map<String, Object> body = new HashMap<>();
        body.put("planCode", code);
        body.put("planName", "重复套餐");
        HttpResponse<String> response = post("/tenant/plan", body);

        assertThat((int) JsonPath.read(response.body(), "$.code")).isNotEqualTo(200);
    }

    @Test
    void assignsAndQueriesPlanMenus() throws Exception {
        long id = createPlan("menu-" + System.currentTimeMillis());

        HttpResponse<String> assigned = put("/tenant/plan/" + id + "/menus",
                Map.of("menuIds", List.of(100, 112, 100)));
        assertThat((int) JsonPath.read(assigned.body(), "$.code")).isEqualTo(200);
        assertThat(longOf(assigned.body(), "$.data.length()")).isEqualTo(2);

        HttpResponse<String> menus = get("/tenant/plan/" + id + "/menus");
        assertThat(longOf(menus.body(), "$.data.length()")).isEqualTo(2);

        Long rows = jdbc.queryForObject("SELECT COUNT(*) FROM t_tenant_plan_menu WHERE plan_id = ?", Long.class, id);
        assertThat(rows).isEqualTo(2);
    }

    @Test
    void bindsPlanAndReturnsEffectiveValues() throws Exception {
        long planId = createPlan("bind-" + System.currentTimeMillis());
        put("/tenant/plan/" + planId + "/menus", Map.of("menuIds", List.of(100, 112)));
        long tenantId = createTenant("bind-co-" + System.currentTimeMillis());

        HttpResponse<String> bound = put("/tenant/" + tenantId + "/plan", Map.of("planId", planId));

        assertThat(longOf(bound.body(), "$.data.planId")).isEqualTo(planId);
        assertThat((String) JsonPath.read(bound.body(), "$.data.planCode")).startsWith("bind-");
        assertThat((int) JsonPath.read(bound.body(), "$.data.maxUsers")).isEqualTo(10);
        assertThat(longOf(bound.body(), "$.data.storageQuotaMb")).isEqualTo(10240L);
        assertThat(longOf(bound.body(), "$.data.maxFileSizeMb")).isEqualTo(20L);
        assertThat((String) JsonPath.read(bound.body(), "$.data.dataScope")).isEqualTo("SELF");
        assertThat(longOf(bound.body(), "$.data.menuIds.length()")).isEqualTo(2);

        HttpResponse<String> effective = get("/tenant/" + tenantId + "/plan");
        assertThat(longOf(effective.body(), "$.data.planId")).isEqualTo(planId);

        Long packageId = jdbc.queryForObject("SELECT package_id FROM t_tenant WHERE id = ?", Long.class, tenantId);
        assertThat(packageId).isEqualTo(planId);
    }

    @Test
    void unbindFallsBackToGlobalDefaults() throws Exception {
        long planId = createPlan("unbind-" + System.currentTimeMillis());
        long tenantId = createTenant("unbind-co-" + System.currentTimeMillis());
        bindPlan(tenantId, planId);

        HttpResponse<String> unbound = put("/tenant/" + tenantId + "/plan", Map.of());

        assertThat((Object) JsonPath.read(unbound.body(), "$.data.planId")).isNull();
        assertThat((Object) JsonPath.read(unbound.body(), "$.data.maxUsers")).isNull();
        assertThat(longOf(unbound.body(), "$.data.maxFileSizeMb")).isEqualTo(100L);
        assertThat(longOf(unbound.body(), "$.data.logRetentionDays")).isEqualTo(90);
        assertThat(longOf(unbound.body(), "$.data.rateLimitPerMinute")).isEqualTo(100);
        assertThat(longOf(unbound.body(), "$.data.maxConcurrentExports")).isEqualTo(5);
        assertThat(longOf(unbound.body(), "$.data.searchMaxPageSize")).isEqualTo(100);

        HttpResponse<String> quota = get("/tenant/" + tenantId + "/quota");
        assertThat((int) JsonPath.read(quota.body(), "$.code")).isEqualTo(200);
    }

    @Test
    void effectiveRejectsUnknownTenant() throws Exception {
        HttpResponse<String> response = get("/tenant/424242/plan");

        assertThat((int) JsonPath.read(response.body(), "$.code")).isNotEqualTo(200);
    }

    @Test
    void refusesDisabledPlanAndTreatsItAsUnbound() throws Exception {
        long planId = createPlan("off-" + System.currentTimeMillis());
        long tenantId = createTenant("off-co-" + System.currentTimeMillis());
        bindPlan(tenantId, planId);

        HttpResponse<String> disabled = put("/tenant/plan/" + planId + "/status?enabled=false", Map.of());
        assertThat((boolean) JsonPath.read(disabled.body(), "$.data.enabled")).isFalse();

        HttpResponse<String> refused = put("/tenant/" + tenantId + "/plan", Map.of("planId", planId));
        assertThat((int) JsonPath.read(refused.body(), "$.code")).isNotEqualTo(200);

        HttpResponse<String> effective = get("/tenant/" + tenantId + "/plan");
        assertThat((Object) JsonPath.read(effective.body(), "$.data.planId")).isNull();
    }

    @Test
    void dynamicConfigOverridesStaticDefaults() throws Exception {
        long tenantId = createTenant("cfg-co-" + System.currentTimeMillis());
        configService.set("modality.tenant-module.plan.max-concurrent-exports", "9");
        try {
            HttpResponse<String> effective = put("/tenant/" + tenantId + "/plan", Map.of());

            assertThat(longOf(effective.body(), "$.data.maxConcurrentExports")).isEqualTo(9);
            assertThat(longOf(effective.body(), "$.data.logRetentionDays")).isEqualTo(90);
        } finally {
            configService.remove("modality.tenant-module.plan.max-concurrent-exports");
        }
    }

    @Test
    void updatesPlanQuotasPartially() throws Exception {
        long id = createPlan("upd-" + System.currentTimeMillis());

        Map<String, Object> body = new HashMap<>();
        body.put("planCode", "upd-" + System.currentTimeMillis() + "-x");
        body.put("planName", "改名套餐");
        body.put("maxUsers", 99);
        HttpResponse<String> updated = put("/tenant/plan/" + id, body);

        assertThat((int) JsonPath.read(updated.body(), "$.code")).isEqualTo(200);
        assertThat((int) JsonPath.read(updated.body(), "$.data.maxUsers")).isEqualTo(99);
        assertThat((String) JsonPath.read(updated.body(), "$.data.planName")).isEqualTo("改名套餐");
        assertThat(longOf(updated.body(), "$.data.maxExportRows")).isEqualTo(2000);
    }

    @Test
    void deletesPlanAndCascadesMenus() throws Exception {
        long id = createPlan("del-" + System.currentTimeMillis());
        put("/tenant/plan/" + id + "/menus", Map.of("menuIds", List.of(100, 112)));

        HttpResponse<String> deleted = delete("/tenant/plan/" + id);
        assertThat((int) JsonPath.read(deleted.body(), "$.code")).isEqualTo(200);
        assertThat((int) JsonPath.read(deleted.body(), "$.data")).isEqualTo(1);

        HttpResponse<String> detail = get("/tenant/plan/" + id);
        assertThat((int) JsonPath.read(detail.body(), "$.code")).isNotEqualTo(200);

        Long rows = jdbc.queryForObject("SELECT COUNT(*) FROM t_tenant_plan_menu WHERE plan_id = ?", Long.class, id);
        assertThat(rows).isZero();
    }
}
