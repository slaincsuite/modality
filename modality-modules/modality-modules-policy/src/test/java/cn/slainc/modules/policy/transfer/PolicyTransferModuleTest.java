package cn.slainc.modules.policy.transfer;

import cn.slainc.modules.policy.service.transfer.PolicyTransferService;

import cn.slainc.excel.ExcelUtils;
import cn.slainc.modules.policy.PolicyModuleApplication;
import cn.slainc.modules.policy.PolicyTestBodies;
import cn.slainc.modules.policy.excel.PolicyExportRow;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// JSON 与 Excel 双通道导入导出往返（真实 Tomcat + H2，无 Redis 路线）
@SpringBootTest(classes = PolicyModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PolicyTransferModuleTest {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private cn.slainc.modules.policy.service.transfer.PolicyTransferService transferService;

    @LocalServerPort
    private int port;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        for (String table : new String[]{"t_policy", "t_policy_version", "t_policy_audit", "t_policy_meta"}) {
            try {
                jdbc.execute("DELETE FROM " + table);
            } catch (org.springframework.dao.DataAccessException ignored) {
                // t_policy 系列表由 policy 库懒建，首个用例前可能尚不存在
            }
        }
        token = JsonPath.read(login(), "$.data");
    }

    @Test
    void jsonRoundTripKeepsDocumentAndGroup() throws Exception {
        String document = PolicyTestBodies.document("allow-read", "allow", "demo:read");
        post("/policy/manage", PolicyTestBodies.createRequest("导出源", "global", null, "导出组", document));
        String exported = get("/policy/transfer/export/json");
        assertThat(exported).contains("导出源").contains("导出组").contains("demo:read");

        jdbc.execute("DELETE FROM t_policy");
        jdbc.execute("DELETE FROM t_policy_meta");
        String importResult = post("/policy/transfer/import/json", exported);
        assertThat(Integer.parseInt(JsonPath.read(importResult, "$.data.succeeded").toString())).isEqualTo(1);
        assertThat(Integer.parseInt(JsonPath.read(importResult, "$.data.failed").toString())).isEqualTo(0);
        assertThat(get("/policy/manage/page?group=导出组")).contains("导出源");
    }

    @Test
    void excelRoundTripThroughService() throws Exception {
        post("/policy/manage", PolicyTestBodies.createRequest("表格源", "user", "92001", "表格组",
                PolicyTestBodies.document("allow-read", "allow", "demo:read")));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transferService.exportExcel(null, output);
        assertThat(output.size()).isGreaterThan(0);
        assertThat(new String(output.toByteArray(), 0, 2)).isEqualTo("PK");

        List<PolicyExportRow> rows = ExcelUtils.read(new ByteArrayInputStream(output.toByteArray()),
                PolicyExportRow.class);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getName()).isEqualTo("表格源");
        assertThat(rows.get(0).getDocument()).contains("demo:read");

        jdbc.execute("DELETE FROM t_policy");
        jdbc.execute("DELETE FROM t_policy_meta");
        var file = new MockMultipartFile("file", "policies.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        var result = transferService.importExcel(file, "tester");
        assertThat(result.get("succeeded")).isEqualTo(1);
        assertThat(Integer.parseInt(JsonPath.read(get("/policy/manage/page?group=表格组"),
                "$.data.total").toString())).isEqualTo(1);
    }

    @Test
    void brokenJsonImportReportsErrors() throws Exception {
        String payload = "{\"items\":[{\"name\":\"坏策略\",\"subjectType\":\"global\","
                + "\"document\":\"{\\\"version\\\":\\\"9.9\\\"}\"}]}";
        String result = post("/policy/transfer/import/json", payload);
        assertThat(Integer.parseInt(JsonPath.read(result, "$.data.failed").toString())).isEqualTo(1);
        assertThat(JsonPath.read(result, "$.data.errors[0]").toString()).contains("坏策略");
    }

    private String login() throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/policy-test/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private String get(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(15)).header("satoken", token).header("X-Login-Type", "TENANT_USER")
                        .GET().build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private String post(String path, String body) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Login-Type", "TENANT_USER")
                .header("satoken", token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return response.body();
    }
}
