package cn.slainc.boot;
import cn.slainc.modules.auth.service.closure.AccountClosureService;

import cn.slainc.captcha.CaptchaChallenge;
import cn.slainc.captcha.CaptchaService;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.auth.service.closure.AccountClosureService;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.modules.upms.security.UpmsAccountAuthProvider;
import cn.slainc.modules.upms.service.user.UserService;
import cn.slainc.oauth2.client.ClientAdminService;
import cn.slainc.oauth2.client.Oauth2ClientRecord;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.thirdauth.ThirdPartyPlatform;
import cn.slainc.thirdauth.ThirdPartyUser;
import cn.slainc.thirdauth.provider.ThirdPartyAuthProvider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 批次 9 整机验收：登录全链路（密码+验证码、扫码、第三方回调、OAuth2 授权确认、Token 生命周期、登出、注销、二次认证）
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "modality.auth.captcha.enabled=true",
        "modality.thirdauth.platform.feishu.enabled=true",
        "modality.thirdauth.platform.feishu.app-key=cli-boot-key",
        "modality.thirdauth.platform.feishu.app-secret=feishu-boot-secret",
        "modality.thirdauth.platform.feishu.callback-base=https://cb.boot.example.com",
        "modality.oauth2.scopes[0].name=openid",
        "modality.oauth2.scopes[0].description=OIDC \\u57fa\\u7840\\u8eab\\u4efd\\u6807\\u8bc6",
        "modality.oauth2.scopes[0].risk-level=LOW",
        "modality.oauth2.scopes[1].name=pay",
        "modality.oauth2.scopes[1].description=\\u53d1\\u8d77\\u652f\\u4ed8\\u4e0e\\u8d44\\u91d1\\u64cd\\u4f5c",
        "modality.oauth2.scopes[1].risk-level=HIGH",
        "modality.auth.closure.cool-down-seconds=3600"
})
@ActiveProfiles("dev")
class Batch9AuthAcceptanceTest {

    private static final String PASSWORD = "Password#123";

    private static final String CLIENT_ID = "batch9-app";

    private static final String CLIENT_SECRET = "batch9-secret";

    @Autowired
    private UserService userService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private ClientAdminService clientAdminService;

    @Autowired
    private AccountClosureService closureService;

    @Autowired
    private cn.slainc.jwt.issue.JwtTokenService jwtTokenService;

    @Autowired
    private Batch9FeishuFakeProvider fakeProvider;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private HttpClient http;

    // 本验收用 t_user 真实账号走完整登录链路，@Primary 压过 Batch4 基座的内存账号提供者
    @TestConfiguration(proxyBeanMethods = false)
    static class DbAccountConfiguration {

        @Bean
        @Primary
        public AccountAuthProvider batch9DbAccountProvider(UserMapper userMapper) {
            return new UpmsAccountAuthProvider(userMapper);
        }

        @Bean
        public Batch9FeishuFakeProvider feishuAuthProvider() {
            return new Batch9FeishuFakeProvider();
        }
    }

    static class Batch9FeishuFakeProvider implements ThirdPartyAuthProvider {

        private ThirdPartyUser nextUser;

        void nextUser(ThirdPartyUser user) {
            this.nextUser = user;
        }

        @Override
        public ThirdPartyPlatform getPlatform() {
            return ThirdPartyPlatform.FEISHU;
        }

        @Override
        public String getAuthorizeUrl(String state, String redirectUri) {
            return "https://fake.feishu.example.com/open-apis/authen/v1/index?app_id=cli-boot-key&state=" + state;
        }

        @Override
        public ThirdPartyUser getUserInfo(String code, String state) {
            if (nextUser != null) {
                return nextUser;
            }
            ThirdPartyUser user = new ThirdPartyUser();
            user.setPlatform(ThirdPartyPlatform.FEISHU);
            user.setOpenId("open-boot");
            user.setUnionId("union-boot");
            user.setNickname("飞书用户");
            return user;
        }
    }

    @BeforeAll
    static void prepare() {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        for (String prefix : new String[]{"satoken:", "modality:security:", "modality:auth:", "modality:captcha:",
                "modality:oauth2:", "modality:thirdauth:", "modality:jwt:"}) {
            var keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
        for (String table : new String[]{"t_auth_account_closure", "t_third_account_binding", "t_consent_record",
                "t_user_login_record", "t_user_operation_log", "t_user"}) {
            try {
                jdbc.execute("DELETE FROM " + table);
            } catch (org.springframework.dao.DataAccessException ignored) {
                // 懒建表，首个用例前可能不存在
            }
        }
        clientAdminService.remove(CLIENT_ID);
        Oauth2ClientRecord client = new Oauth2ClientRecord();
        client.setClientId(CLIENT_ID);
        client.setClientSecret(CLIENT_SECRET);
        client.setClientName("批次九验收应用");
        client.setContractScopes(List.of("openid", "pay"));
        client.setAllowRedirectUris(List.of("http://callback.example.com/cb"));
        client.setAllowGrantTypes(List.of("authorization_code", "password"));
        clientAdminService.register(client);
        http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Test
    void passwordLoginWithCaptchaAndForcedPasswordChange() throws Exception {
        UserRecord user = createUser("batch9pwd", PASSWORD);
        HttpResponse<String> rejected = post("/auth/login",
                "{\"username\":\"batch9pwd\",\"password\":\"" + PASSWORD + "\"}", null, null);
        assertThat(rejected.statusCode()).as(rejected.body()).isEqualTo(400);
        assertThat(rejected.body()).contains("请输入验证码");

        HttpResponse<String> loginResponse = login("batch9pwd", PASSWORD);
        assertThat((Boolean) JsonPath.<Boolean>read(loginResponse.body(), "$.data.mustChangePassword"))
                .as("新用户未改过密码不应强制").isFalse();

        userService.update(user.getId(), null, null, null, null, null, null, null, "10.77.0.1", null, null);
        HttpResponse<String> restricted = loginRaw("batch9pwd", PASSWORD);
        assertThat(restricted.statusCode()).as(restricted.body()).isEqualTo(400);
        assertThat(restricted.body()).contains("不在账号允许的 IP");
        userService.update(user.getId(), null, null, null, null, null, null, null, "", null, null);

        String token = tokenOf(loginResponse);
        HttpResponse<String> me = get("/batch4/me", token, null);
        assertThat(me.statusCode()).as(me.body()).isEqualTo(200);

        HttpResponse<String> change = post("/auth/password/change",
                "{\"oldPassword\":\"" + PASSWORD + "\",\"newPassword\":\"Fresh#Pass456\"}", token, null);
        assertThat(change.statusCode()).as(change.body()).isEqualTo(200);
        HttpResponse<String> relogin = login("batch9pwd", "Fresh#Pass456");
        assertThat((Boolean) JsonPath.<Boolean>read(relogin.body(), "$.data.mustChangePassword")).isFalse();

        Integer loginRecords = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_user_login_record WHERE username = 'batch9pwd' AND succeeded = 1",
                Integer.class);
        assertThat(loginRecords).isGreaterThanOrEqualTo(2);
    }

    @Test
    void qrLoginChainEstablishesSessionWithoutPassword() throws Exception {
        UserRecord user = createUser("batch9qr", PASSWORD);
        String mobileToken = tokenOf(login("batch9qr", PASSWORD));

        HttpResponse<String> create = post("/auth/qrcode", "{}", null, null);
        assertThat(create.statusCode()).as(create.body()).isEqualTo(200);
        String ticketId = JsonPath.<String>read(create.body(), "$.data.ticketId");
        byte[] png = Base64.getDecoder().decode(JsonPath.<String>read(create.body(), "$.data.imageBase64"));
        assertThat(new String(new byte[]{png[1], png[2], png[3]}, "US-ASCII")).isEqualTo("PNG");
        assertThat(statusOf(ticketId)).isEqualTo("WAITING");

        assertThat(post("/auth/qrcode/" + ticketId + "/scan", "{}", mobileToken, null).statusCode()).isEqualTo(200);
        assertThat(statusOf(ticketId)).isEqualTo("SCANNED");
        assertThat(post("/auth/qrcode/" + ticketId + "/confirm", "{}", mobileToken, null).statusCode()).isEqualTo(200);
        assertThat(statusOf(ticketId)).isEqualTo("CONFIRMED");

        HttpResponse<String> exchange = post("/auth/qrcode/" + ticketId + "/exchange",
                "{\"deviceType\":\"PC\",\"deviceId\":\"pc-9\"}", null, null);
        assertThat(exchange.statusCode()).as(exchange.body()).isEqualTo(200);
        String pcToken = tokenOf(exchange);
        assertThat(get("/batch4/me", pcToken, null).body()).contains(String.valueOf(user.getId()));

        HttpResponse<String> replay = post("/auth/qrcode/" + ticketId + "/exchange", "{}", null, null);
        assertThat(replay.statusCode()).as(replay.body()).isEqualTo(400);
    }

    @Test
    void thirdPartyCallbackBindsAccountAndLogsIn() throws Exception {
        createUser("batch9third", PASSWORD);
        fakeProvider.nextUser(feishuUser("open-b9", "union-b9"));

        HttpResponse<String> authorize = get("/auth/third/feishu/authorize-url", null, null);
        assertThat(authorize.statusCode()).as(authorize.body()).isEqualTo(200);
        String state = JsonPath.<String>read(authorize.body(), "$.data.state");

        HttpResponse<String> callback = get("/auth/third/callback/feishu?code=fs-code&state=" + state, null, null);
        assertThat(callback.statusCode()).as(callback.body()).isEqualTo(200);
        assertThat((Boolean) JsonPath.<Boolean>read(callback.body(), "$.data.needBind")).isTrue();
        String bindTicket = JsonPath.<String>read(callback.body(), "$.data.bindTicketId");

        HttpResponse<String> bind = post("/auth/third/bind",
                "{\"bindTicketId\":\"" + bindTicket + "\",\"username\":\"batch9third\",\"password\":\"" + PASSWORD
                        + "\"}", null, null);
        assertThat(bind.statusCode()).as(bind.body()).isEqualTo(200);
        String token = tokenOf(bind);
        assertThat(get("/batch4/me", token, null).statusCode()).isEqualTo(200);

        HttpResponse<String> bindings = get("/auth/third/bindings", token, null);
        assertThat(bindings.body()).contains("open-b9");

        String secondState = JsonPath.<String>read(
                get("/auth/third/feishu/authorize-url", null, null).body(), "$.data.state");
        HttpResponse<String> second = get("/auth/third/callback/feishu?code=fs-code-2&state=" + secondState, null,
                null);
        assertThat((Boolean) JsonPath.<Boolean>read(second.body(), "$.data.needBind")).isFalse();
        assertThat(JsonPath.<String>read(second.body(), "$.data.login.tokenValue")).isNotBlank();
    }

    @Test
    void pausedPlatformsRejectedOnAuthEntryPoints() throws Exception {
        HttpResponse<String> authorize = get("/auth/third/wechat/authorize-url", null, null);
        assertThat(authorize.statusCode()).as(authorize.body()).isEqualTo(400);
        assertThat(authorize.body()).contains("不支持的第三方平台");
        assertThat(authorize.body()).contains("feishu");

        HttpResponse<String> callback = get("/auth/third/callback/wechat?code=wx-code&state=wx-state", null, null);
        assertThat(callback.statusCode()).as(callback.body()).isEqualTo(400);
        assertThat(callback.body()).contains("不支持的第三方平台");

        HttpResponse<String> platforms = get("/auth/third/platforms", null, null);
        assertThat(platforms.statusCode()).as(platforms.body()).isEqualTo(200);
        assertThat(platforms.body()).contains("feishu");
        assertThat(platforms.body()).doesNotContain("wechat");
        assertThat(platforms.body()).doesNotContain("dingtalk");
    }

    @Test
    void oauth2AuthorizeConfirmPageAndTokenIssuance() throws Exception {
        createUser("batch9oa", PASSWORD);
        String token = tokenOf(login("batch9oa", PASSWORD));

        String authorizeUrl = "/oauth2/authorize?response_type=code&client_id=" + CLIENT_ID
                + "&redirect_uri=" + encode("http://callback.example.com/cb")
                + "&scope=" + encode("openid pay") + "&state=b9";
        HttpResponse<String> confirmPage = get(authorizeUrl, token, null);
        assertThat(confirmPage.statusCode()).as(confirmPage.body()).isEqualTo(200);
        assertThat(confirmPage.body()).contains("需要您确认授权");
        assertThat(confirmPage.body()).contains("发起支付与资金操作");
        assertThat(confirmPage.body()).contains("高风险");

        HttpResponse<String> confirm = postForm("/oauth2/doConfirm",
                "response_type=code&client_id=" + CLIENT_ID
                        + "&redirect_uri=" + encode("http://callback.example.com/cb")
                        + "&scope=" + encode("openid pay") + "&build_redirect_uri=true", token);
        assertThat(confirm.statusCode()).as(confirm.body()).isEqualTo(200);
        String code = paramFromUrl(JsonPath.<String>read(confirm.body(), "$.redirect_uri"), "code");

        HttpResponse<String> tokenResponse = postForm("/oauth2/token",
                "grant_type=authorization_code&code=" + code
                        + "&redirect_uri=" + encode("http://callback.example.com/cb")
                        + "&client_id=" + CLIENT_ID + "&client_secret=" + CLIENT_SECRET, null);
        assertThat((int) JsonPath.<Integer>read(tokenResponse.body(), "$.code")).as(tokenResponse.body()).isEqualTo(200);
        assertThat(JsonPath.<String>read(tokenResponse.body(), "$.access_token")).isNotBlank();

        HttpResponse<String> jwks = get("/oauth2/jwks", null, null);
        assertThat(jwks.statusCode()).isEqualTo(200);
        assertThat(jwks.body()).contains("\"kty\"");

        HttpResponse<String> consents = get("/auth/oauth2/consents", token, null);
        assertThat(consents.body()).contains(CLIENT_ID);
        HttpResponse<String> revoke = delete("/auth/oauth2/consents/" + CLIENT_ID, token);
        assertThat(revoke.statusCode()).as(revoke.body()).isEqualTo(200);
        assertThat(get(authorizeUrl + "x", token, null).body()).contains("需要您确认授权");
    }

    @Test
    void jwtTokenRefreshRevokeAndIntrospect() throws Exception {
        var refreshToken = jwtTokenService.issueToken(cn.slainc.jwt.issue.TokenIssuanceSpec.builder("99001")
                .tokenType(cn.slainc.jwt.issue.TokenType.REFRESH).ttl(Duration.ofDays(1)).build());

        HttpResponse<String> refresh = post("/auth/token/refresh",
                "{\"token\":\"" + refreshToken.tokenValue() + "\"}", null, null);
        assertThat(refresh.statusCode()).as(refresh.body()).isEqualTo(200);
        String accessToken = JsonPath.<String>read(refresh.body(), "$.data.accessToken");
        String rotated = JsonPath.<String>read(refresh.body(), "$.data.refreshToken");
        assertThat(accessToken).isNotBlank();
        assertThat(rotated).isNotEqualTo(refreshToken.tokenValue());

        createUser("batch9token", PASSWORD);
        String session = tokenOf(login("batch9token", PASSWORD));

        HttpResponse<String> anonymous = post("/auth/token/introspect", introspectBody(accessToken), null, null);
        assertThat(anonymous.statusCode()).as(anonymous.body()).isEqualTo(401);

        HttpResponse<String> withoutClientAuth = post("/auth/token/introspect", "{\"token\":\"" + accessToken + "\"}",
                session, null);
        assertThat(withoutClientAuth.statusCode()).as(withoutClientAuth.body()).isEqualTo(401);

        HttpResponse<String> introspect = post("/auth/token/introspect", introspectBody(accessToken), session, null);
        assertThat(introspect.statusCode()).as(introspect.body()).isEqualTo(200);
        assertThat((Boolean) JsonPath.<Boolean>read(introspect.body(), "$.data.active")).isTrue();

        assertThat(post("/auth/token/revoke", "{\"token\":\"" + accessToken + "\"}", null, null).statusCode())
                .isEqualTo(401);
        assertThat(post("/auth/token/revoke", "{\"token\":\"" + accessToken + "\"}", session, null).statusCode())
                .isEqualTo(200);
        HttpResponse<String> afterRevoke = post("/auth/token/introspect", introspectBody(accessToken), session, null);
        assertThat((Boolean) JsonPath.<Boolean>read(afterRevoke.body(), "$.data.active")).isFalse();
    }

    private static String introspectBody(String token) {
        return "{\"token\":\"" + token + "\",\"clientId\":\"" + CLIENT_ID + "\",\"clientSecret\":\""
                + CLIENT_SECRET + "\"}";
    }

    @Test
    void logoutSingleAllAndOperationLogRecorded() throws Exception {
        createUser("batch9out", PASSWORD);
        String first = tokenOf(login("batch9out", PASSWORD));
        String second = tokenOf(login("batch9out", PASSWORD));

        assertThat(post("/auth/logout", "{}", first, null).statusCode()).isEqualTo(200);
        assertThat(get("/batch4/me", first, null).statusCode()).isEqualTo(401);
        assertThat(get("/batch4/me", second, null).statusCode()).isEqualTo(200);

        assertThat(post("/auth/logout/all", "{}", second, null).statusCode()).isEqualTo(200);
        assertThat(get("/batch4/me", second, null).statusCode()).isEqualTo(401);

        Integer logoutLogs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_user_operation_log WHERE module_name = 'auth' AND operation = '登出'",
                Integer.class);
        assertThat(logoutLogs).isGreaterThanOrEqualTo(2);
    }

    @Test
    void safeAuthOpensWindowAfterPasswordRecheck() throws Exception {
        createUser("batch9safe", PASSWORD);
        String token = tokenOf(login("batch9safe", PASSWORD));

        HttpResponse<String> wrong = post("/auth/safe/open", "{\"password\":\"Wrong#123\"}", token, null);
        assertThat(wrong.statusCode()).as(wrong.body()).isEqualTo(400);

        HttpResponse<String> open = post("/auth/safe/open", "{\"password\":\"" + PASSWORD + "\"}", token, null);
        assertThat(open.statusCode()).as(open.body()).isEqualTo(200);
        HttpResponse<String> status = get("/auth/safe/status", token, null);
        assertThat(status.body()).contains("true");

        assertThat(post("/auth/safe/close", "{}", token, null).statusCode()).isEqualTo(200);
        assertThat(get("/auth/safe/status", token, null).body()).contains("false");
    }

    @Test
    void rememberMeGrantsLongLivedToken() throws Exception {
        createUser("batch9rem", PASSWORD);
        CaptchaChallenge challenge = captchaService.generate("WEB", "127.0.0.1");
        String answer = captchaAnswer(challenge);
        HttpResponse<String> login = post("/auth/login",
                "{\"username\":\"batch9rem\",\"password\":\"" + PASSWORD + "\",\"remember\":true,\"captchaId\":\""
                        + challenge.id() + "\",\"captchaCode\":\"" + answer + "\"}", null, null);
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        assertThat((Integer) JsonPath.<Integer>read(login.body(), "$.data.tokenTimeout"))
                .isGreaterThanOrEqualTo(2592000);
    }

    @Test
    void accountClosureCoolDownAndSoftDelete() throws Exception {
        UserRecord user = createUser("batch9close", PASSWORD);
        String token = tokenOf(login("batch9close", PASSWORD));

        HttpResponse<String> apply = post("/auth/account/close",
                "{\"password\":\"" + PASSWORD + "\",\"reason\":\"整机验收注销\"}", token, null);
        assertThat(apply.statusCode()).as(apply.body()).isEqualTo(200);
        assertThat(apply.body()).contains("PENDING");
        assertThat(closureService.executeDue()).isZero();

        jdbc.update("UPDATE t_auth_account_closure SET cool_down_until = ? WHERE user_id = ? AND status = 'PENDING'",
                System.currentTimeMillis() - 1000, user.getId());
        assertThat(closureService.executeDue()).isEqualTo(1);
        Integer deleted = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_user WHERE id = ? AND deleted = 1", Integer.class, user.getId());
        assertThat(deleted).isEqualTo(1);
    }

    private UserRecord createUser(String username, String password) {
        return userService.create(username, username, password, null, null, null, LoginType.TENANT_USER, 1L,
                "batch9");
    }

    private ThirdPartyUser feishuUser(String openId, String unionId) {
        ThirdPartyUser user = new ThirdPartyUser();
        user.setPlatform(ThirdPartyPlatform.FEISHU);
        user.setOpenId(openId);
        user.setUnionId(unionId);
        user.setNickname("飞书昵称" + openId);
        return user;
    }

    private String captchaAnswer(CaptchaChallenge challenge) {
        String raw = redis.opsForValue().get("modality:captcha:data:WEB:" + challenge.id());
        assertThat(raw).as("验证码答案只存服务端，从存储侧取证").isNotNull();
        return JsonPath.<String>read(raw, "$.answer");
    }

    private HttpResponse<String> login(String username, String password) throws Exception {
        CaptchaChallenge challenge = captchaService.generate("WEB", "127.0.0.1");
        HttpResponse<String> response = post("/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"captchaId\":\""
                        + challenge.id() + "\",\"captchaCode\":\"" + captchaAnswer(challenge) + "\"}", null, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return response;
    }

    private String tokenOf(HttpResponse<String> response) {
        return JsonPath.<String>read(response.body(), "$.data.tokenValue");
    }

    private HttpResponse<String> loginRaw(String username, String password) throws Exception {
        CaptchaChallenge challenge = captchaService.generate("WEB", "127.0.0.1");
        return post("/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"captchaId\":\""
                        + challenge.id() + "\",\"captchaCode\":\"" + captchaAnswer(challenge) + "\"}", null,
                null);
    }

    private String statusOf(String ticketId) throws Exception {
        return JsonPath.<String>read(get("/auth/qrcode/" + ticketId, null, null).body(), "$.data.status");
    }

    private HttpResponse<String> get(String path, String token, String type) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (token != null) {
            builder.header("satoken", token);
        }
        if (type != null) {
            builder.header("X-Login-Type", type);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String token, String type) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header("satoken", token);
        }
        if (type != null) {
            builder.header("X-Login-Type", type);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postForm(String path, String form, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form));
        if (token != null) {
            builder.header("satoken", token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("satoken", token)
                .DELETE()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String paramFromUrl(String url, String name) {
        int queryIndex = url.indexOf('?');
        String query = queryIndex >= 0 ? url.substring(queryIndex + 1) : url;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
