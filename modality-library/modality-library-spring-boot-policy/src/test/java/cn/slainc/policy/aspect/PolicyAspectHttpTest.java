package cn.slainc.policy.aspect;

import cn.slainc.policy.PolicyTestApplication;
import cn.slainc.policy.admin.PolicyAdminService;
import cn.slainc.policy.domain.Effect;
import cn.slainc.policy.domain.PolicyDocument;
import cn.slainc.policy.domain.PolicyStatement;
import cn.slainc.policy.domain.PolicySubjectType;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = PolicyTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PolicyAspectHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private PolicyAdminService adminService;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        PolicyStatement read = statement(Effect.ALLOW, List.of("modality:policy-test:read"), List.of("*"));
        PolicyStatement doc = statement(Effect.ALLOW, List.of("modality:policy-test:doc"), List.of("modality:doc/7"));
        adminService.create("用户读权限", PolicySubjectType.USER, "10001", "TENANT_USER",
                PolicyDocument.of(read), null, "测试");
        adminService.create("文档七权限", PolicySubjectType.USER, "10001", "TENANT_USER",
                PolicyDocument.of(doc), null, "测试");
        adminService.create("全局禁写", PolicySubjectType.GLOBAL, null, null,
                PolicyDocument.of(statement(Effect.DENY, List.of("modality:policy-test:write"), List.of("*"))),
                null, "测试");
        HttpResponse<String> loginResponse = exchange("/policy-test/login", null);
        assertEquals(200, loginResponse.statusCode());
        token = JsonPath.read(loginResponse.body(), "$.data");
    }

    @Test
    void allowedPolicyPassesRealHttp() throws Exception {
        HttpResponse<String> response = exchange("/policy-test/read", token);
        assertEquals(200, response.statusCode());
        assertEquals("read-ok", JsonPath.read(response.body(), "$.data"));
        assertEquals(200, (int) JsonPath.read(response.body(), "$.code"));
    }

    @Test
    void denyPolicyReturnsReal403With403000() throws Exception {
        HttpResponse<String> response = exchange("/policy-test/write", token);
        assertEquals(403, response.statusCode());
        assertEquals(403000, (int) JsonPath.read(response.body(), "$.code"));
    }

    @Test
    void resourcePlaceholderResolvedFromMethodArgs() throws Exception {
        assertEquals(200, exchange("/policy-test/doc/7", token).statusCode());
        HttpResponse<String> denied = exchange("/policy-test/doc/8", token);
        assertEquals(403, denied.statusCode());
        assertEquals(403000, (int) JsonPath.read(denied.body(), "$.code"));
    }

    @Test
    void anonymousRequestReturnsReal401() throws Exception {
        HttpResponse<String> response = exchange("/policy-test/read", null);
        assertEquals(401, response.statusCode());
        assertEquals(401000, (int) JsonPath.read(response.body(), "$.code"));
    }

    @Test
    void newDenyPolicyTakesEffectImmediately() throws Exception {
        assertEquals(200, exchange("/policy-test/read", token).statusCode());
        String denyId = adminService.create("禁读策略", PolicySubjectType.GLOBAL, null, null,
                PolicyDocument.of(statement(Effect.DENY, List.of("modality:policy-test:read"), List.of("*"))),
                null, "测试").getId();
        try {
            HttpResponse<String> response = exchange("/policy-test/read", token);
            assertEquals(403, response.statusCode());
        } finally {
            adminService.delete(denyId, "测试");
        }
    }

    private HttpResponse<String> exchange(String path, String tokenValue) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (tokenValue != null) {
            builder.header("satoken", tokenValue).header("X-Login-Type", "TENANT_USER");
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private PolicyStatement statement(Effect effect, List<String> actions, List<String> resources) {
        PolicyStatement statement = new PolicyStatement();
        statement.setSid("s-" + System.nanoTime());
        statement.setEffect(effect);
        statement.setActions(actions);
        statement.setResources(resources);
        return statement;
    }
}
