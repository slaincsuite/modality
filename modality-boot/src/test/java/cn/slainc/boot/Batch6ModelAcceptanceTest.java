package cn.slainc.boot;

import cn.slainc.core.exception.ThirdPartyException;
import cn.slainc.oauth2.client.ClientAdminService;
import cn.slainc.oauth2.client.Oauth2ClientRecord;
import cn.slainc.openapi.domain.App;
import cn.slainc.openapi.domain.AppCredential;
import cn.slainc.openapi.domain.AppType;
import cn.slainc.openapi.domain.OpenScope;
import cn.slainc.openapi.spi.AppService;
import cn.slainc.openapi.spi.ConsentService;
import cn.slainc.openapi.spi.CredentialService;
import cn.slainc.openapi.spi.ScopeService;
import cn.slainc.policy.support.PolicyAdminService;
import cn.slainc.policy.domain.Effect;
import cn.slainc.policy.domain.PolicyDocument;
import cn.slainc.policy.domain.PolicyStatement;
import cn.slainc.policy.domain.PolicySubjectType;

import cn.slainc.thirdauth.ThirdAuthService;
import cn.slainc.thirdauth.ThirdPartyPlatform;
import cn.slainc.thirdauth.ThirdPartyUser;
import cn.slainc.thirdauth.qr.QrTicket;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "modality.security.filter.exclude-patterns=/actuator/**,/v3/api-docs/**,/swagger-ui/**,/swagger-ui.html,"
                + "/webjars/**,/favicon.ico,/error,/captcha/**,/auth/login,/auth/logout,/oauth2/jwks,"
                + "/batch6/**",
        "modality.thirdauth.platform.wechat.app-key=wx-boot-key",
        "modality.thirdauth.platform.wechat.app-secret=wx-boot-secret",
        "modality.thirdauth.platform.wechat.callback-base=http://127.0.0.1:18080",
        "modality.thirdauth.platform.wechat-work.app-key=corp-boot-key",
        "modality.thirdauth.platform.wechat-work.app-secret=corp-boot-secret",
        "modality.thirdauth.platform.wechat-work.agent-id=1000002",
        "modality.thirdauth.platform.wechat-work.callback-base=http://127.0.0.1:18080",
        "modality.thirdauth.platform.feishu.app-key=cli-boot-key",
        "modality.thirdauth.platform.feishu.app-secret=feishu-boot-secret",
        "modality.thirdauth.platform.feishu.callback-base=http://127.0.0.1:18080",
        "modality.thirdauth.platform.dingtalk.app-key=ding-boot-key",
        "modality.thirdauth.platform.dingtalk.app-secret=ding-boot-secret",
        "modality.thirdauth.platform.dingtalk.callback-base=http://127.0.0.1:18080"
})
@ActiveProfiles("dev")
class Batch6ModelAcceptanceTest {

    @LocalServerPort
    private int port;

    @Autowired
    private PolicyAdminService policyAdminService;

    @Autowired
    private AppService appService;

    @Autowired
    private ScopeService scopeService;

    @Autowired
    private ConsentService consentService;

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private ClientAdminService clientAdminService;

    @Autowired
    private ThirdAuthService thirdAuthService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void denyPrecedenceAndAspectWorkOverRealHttp() throws Exception {
        policyAdminService.create("批次六读权限", PolicySubjectType.USER, "2001", "TENANT_USER",
                document(Effect.ALLOW, "modality:batch6:data:read"), null, "batch6");
        policyAdminService.create("批次六禁写", PolicySubjectType.GLOBAL, null, null,
                document(Effect.DENY, "modality:batch6:data:write"), null, "batch6");
        String token = login();

        HttpResponse<String> read = exchange("/batch6/read", token);
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(read.body(), "$.data")).isEqualTo("read-ok");

        HttpResponse<String> write = exchange("/batch6/write", token);
        assertThat(write.statusCode()).isEqualTo(403);
        assertThat((int) JsonPath.read(write.body(), "$.code")).isEqualTo(403000);
    }

    @Test
    void noMatchingPolicyDefaultsToDeny() throws Exception {
        String token = login();
        HttpResponse<String> response = exchange("/batch6/unmanaged", token);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat((int) JsonPath.read(response.body(), "$.code")).isEqualTo(403000);
    }

    @Test
    void openApiAppIssuesRealOauth2CredentialAndRotates() {
        OpenScope scope = new OpenScope();
        scope.setName("batch6_read");
        scope.setTitle("批次六读取");
        scopeService.register(scope);

        App app = App.create("批次六应用-" + System.nanoTime(), AppType.WEB, "2001");
        app.setScopes(List.of("batch6_read"));
        app.setRedirectUris(List.of("https://cb.example.com/batch6"));
        appService.create(app);
        appService.submit(app.getAppId());
        appService.approve(app.getAppId());

        AppCredential credential = credentialService.issue(app.getAppId());
        assertThat(credential.getType()).isEqualTo(AppCredential.Type.OAUTH2);

        Oauth2ClientRecord record = clientAdminService.find(credential.getClientId());
        assertThat(record.getClientName()).isEqualTo(app.getName());
        assertThat(record.getContractScopes()).containsExactly("batch6_read");
        assertThat(record.getSubjectId()).isEqualTo("2001");

        AppCredential rotated = credentialService.rotate(app.getAppId(), 600000L);
        assertThat(rotated.getClientSecret()).isNotEqualTo(credential.getClientSecret());
        assertThat(clientAdminService.find(credential.getClientId()).getPreviousSecretExpireAt())
                .isGreaterThan(System.currentTimeMillis());
    }

    @Test
    void openApiConsentGrantRevokeLoop() {
        String suffix = String.valueOf(System.nanoTime());
        OpenScope scope = new OpenScope();
        scope.setName("consent_" + suffix);
        scope.setTitle("授权" + suffix);
        scopeService.register(scope);

        App app = App.create("授权应用-" + suffix, AppType.WEB, "2001");
        app.setScopes(List.of(scope.getName()));
        appService.create(app);
        appService.submit(app.getAppId());
        appService.approve(app.getAppId());

        consentService.grant("2001", app.getAppId(), List.of(scope.getName()), null);
        assertThat(consentService.isGranted("2001", app.getAppId(), scope.getName())).isTrue();

        consentService.revoke("2001", app.getAppId());
        assertThat(consentService.isGranted("2001", app.getAppId(), scope.getName())).isFalse();
    }

    @Test
    void fourPlatformAuthorizeUrlsGeneratedCorrectly() {
        ThirdAuthService.ThirdAuthLoginContext wechat = thirdAuthService.beginLogin(ThirdPartyPlatform.WECHAT);
        assertThat(URI.create(wechat.authorizeUrl()).getHost()).isEqualTo("open.weixin.qq.com");
        assertThat(wechat.authorizeUrl()).contains("wx-boot-key");
        assertThat(wechat.state()).hasSize(32);

        ThirdAuthService.ThirdAuthLoginContext work = thirdAuthService.beginLogin(ThirdPartyPlatform.WECHAT_WORK);
        assertThat(URI.create(work.authorizeUrl()).getHost()).isEqualTo("open.weixin.qq.com");
        assertThat(work.authorizeUrl()).contains("agentid=1000002");

        ThirdAuthService.ThirdAuthLoginContext feishu = thirdAuthService.beginLogin(ThirdPartyPlatform.FEISHU);
        assertThat(URI.create(feishu.authorizeUrl()).getHost()).isEqualTo("open.feishu.cn");

        ThirdAuthService.ThirdAuthLoginContext dingtalk = thirdAuthService.beginLogin(ThirdPartyPlatform.DINGTALK);
        assertThat(URI.create(dingtalk.authorizeUrl()).getHost()).isEqualTo("login.dingtalk.com");
    }

    @Test
    void forgedStateBlocked() {
        ThirdPartyException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ThirdPartyException.class,
                () -> thirdAuthService.handleCallback("wechat", Map.of("code", "c", "state", "forged-state")));
        assertThat(exception.getMessage()).contains("state 无效或已过期");
    }

    @Test
    void bindingPersistedAndQrTicketLoop() {
        ThirdPartyUser user = new ThirdPartyUser();
        user.setPlatform(ThirdPartyPlatform.WECHAT);
        user.setOpenId("open-batch6-" + System.nanoTime());
        user.setUnionId("union-batch6");
        user.setNickname("批次六用户");
        thirdAuthService.bind(user, "2001");

        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_third_account_binding WHERE user_id = '2001' AND union_id = 'union-batch6'",
                Long.class);
        assertThat(rows).isEqualTo(1L);
        assertThat(thirdAuthService.listBindings("2001")).isNotEmpty();
        assertThat(thirdAuthService.findByUnionId(ThirdPartyPlatform.WECHAT, "union-batch6")).isPresent();

        ThirdAuthService.ThirdAuthLoginContext qr = thirdAuthService.beginQrLogin(ThirdPartyPlatform.FEISHU);
        QrTicket ticket = thirdAuthService.pollQr(qr.state());
        assertThat(ticket.getStatus()).isEqualTo(QrTicket.Status.WAITING.name());
    }

    private PolicyDocument document(Effect effect, String action) {
        PolicyStatement statement = new PolicyStatement();
        statement.setSid("batch6-" + System.nanoTime());
        statement.setEffect(effect);
        statement.setActions(List.of(action));
        statement.setResources(List.of("*"));
        return PolicyDocument.of(statement);
    }

    private String login() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"user\",\"password\":\"" + Batch4TestAccountConfiguration.PASSWORD
                                        + "\",\"deviceType\":\"PC\",\"deviceId\":\"batch6-" + System.nanoTime()
                                        + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.data.tokenValue");
    }

    private HttpResponse<String> exchange(String path, String token) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .header("satoken", token)
                        .header("X-Login-Type", "TENANT_USER")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
