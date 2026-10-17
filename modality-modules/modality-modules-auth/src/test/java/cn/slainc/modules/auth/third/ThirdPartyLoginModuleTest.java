package cn.slainc.modules.auth.third;

import cn.slainc.core.context.TenantContext;
import cn.slainc.modules.auth.AuthModuleApplication;
import cn.slainc.modules.auth.FakeFeishuProvider;
import cn.slainc.modules.auth.TestAccountRegistry;
import cn.slainc.security.spi.PasswordAccount;
import cn.slainc.thirdauth.ThirdPartyPlatform;
import cn.slainc.thirdauth.config.ThirdAuthKeys;
import cn.slainc.thirdauth.ThirdPartyUser;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AuthModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ThirdPartyLoginModuleTest {

    private static final String PASSWORD = "Password#123";

    @Autowired
    private TestAccountRegistry registry;

    @Autowired
    private FakeFeishuProvider fakeProvider;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private HttpClient http;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        for (String prefix : new String[]{"satoken:", "modality:security:", "modality:auth:", "modality:thirdauth:"}) {
            var keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
        for (String table : new String[]{"t_third_account_binding"}) {
            try {
                jdbc.execute("DELETE FROM " + table);
            } catch (org.springframework.dao.DataAccessException ignored) {
                // thirdauth 绑定表懒建，首个用例前可能不存在
            }
        }
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    private PasswordAccount createUser(String username) {
        return registry.create(username);
    }

    @Test
    void unboundCallbackIssuesBindTicketThenBindEstablishesSession() throws Exception {
        PasswordAccount user = createUser("binduser");
        fakeProvider.nextUser(userOf("open-100", "union-100"));

        HttpResponse<String> authorize = get("/auth/third/feishu/authorize-url", null);
        assertThat(authorize.statusCode()).as(authorize.body()).isEqualTo(200);
        String state = JsonPath.<String>read(authorize.body(), "$.data.state");
        assertThat(JsonPath.<String>read(authorize.body(), "$.data.authorizeUrl")).contains(state);

        HttpResponse<String> callback = get("/auth/third/callback/feishu?code=auth-code-1&state=" + state, null);
        assertThat(callback.statusCode()).as(callback.body()).isEqualTo(200);
        assertThat((Boolean) JsonPath.<Boolean>read(callback.body(), "$.data.needBind")).isTrue();
        String bindTicket = JsonPath.<String>read(callback.body(), "$.data.bindTicketId");
        assertThat(bindTicket).isNotBlank();
        assertThat(callback.body()).contains("open-100");

        HttpResponse<String> bind = post("/auth/third/bind",
                "{\"bindTicketId\":\"" + bindTicket + "\",\"username\":\"binduser\",\"password\":\"" + PASSWORD
                        + "\"}", null);
        assertThat(bind.statusCode()).as(bind.body()).isEqualTo(200);
        String token = JsonPath.<String>read(bind.body(), "$.data.tokenValue");
        assertThat(token).isNotBlank();

        HttpResponse<String> me = get("/auth/test/me", token);
        assertThat(me.statusCode()).as(me.body()).isEqualTo(200);
        assertThat(me.body()).contains(String.valueOf(user.userId()));

        HttpResponse<String> bindings = get("/auth/third/bindings", token);
        assertThat(bindings.statusCode()).as(bindings.body()).isEqualTo(200);
        assertThat(bindings.body()).contains("open-100");

        HttpResponse<String> unbind = http.send(HttpRequest.newBuilder(
                                URI.create("http://127.0.0.1:" + port + "/auth/third/bindings/feishu/open-100"))
                        .header("satoken", token)
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(unbind.statusCode()).as(unbind.body()).isEqualTo(200);
    }

    @Test
    void boundAccountLogsInDirectlyOnCallback() throws Exception {
        createUser("bounduser");
        fakeProvider.nextUser(userOf("open-200", "union-200"));
        String state = stateOf();

        HttpResponse<String> first = get("/auth/third/callback/feishu?code=code-a&state=" + state, null);
        assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
        String bindTicket = JsonPath.<String>read(first.body(), "$.data.bindTicketId");
        HttpResponse<String> bind = post("/auth/third/bind",
                "{\"bindTicketId\":\"" + bindTicket + "\",\"username\":\"bounduser\",\"password\":\"" + PASSWORD
                        + "\"}", null);
        assertThat(bind.statusCode()).as(bind.body()).isEqualTo(200);

        String secondState = stateOf();
        HttpResponse<String> second = get("/auth/third/callback/feishu?code=code-b&state=" + secondState, null);
        assertThat(second.statusCode()).as(second.body()).isEqualTo(200);
        assertThat((Boolean) JsonPath.<Boolean>read(second.body(), "$.data.needBind")).isFalse();
        String token = JsonPath.<String>read(second.body(), "$.data.login.tokenValue");
        assertThat(get("/auth/test/me", token).statusCode()).isEqualTo(200);
    }

    @Test
    void bindTicketSurvivesWrongPasswordAndIsConsumedOnSuccess() throws Exception {
        createUser("victim");
        fakeProvider.nextUser(userOf("open-300", "union-300"));
        String state = stateOf();
        HttpResponse<String> callback = get("/auth/third/callback/feishu?code=code-c&state=" + state, null);
        String bindTicket = JsonPath.<String>read(callback.body(), "$.data.bindTicketId");

        HttpResponse<String> wrongPassword = post("/auth/third/bind",
                "{\"bindTicketId\":\"" + bindTicket + "\",\"username\":\"victim\",\"password\":\"Wrong#123\"}", null);
        assertThat(wrongPassword.statusCode()).as(wrongPassword.body()).isEqualTo(400);
        assertThat(wrongPassword.body()).contains("账号不存在或密码错误");

        HttpResponse<String> retry = post("/auth/third/bind",
                "{\"bindTicketId\":\"" + bindTicket + "\",\"username\":\"victim\",\"password\":\"" + PASSWORD
                        + "\"}", null);
        assertThat(retry.statusCode()).as(retry.body()).isEqualTo(200);
        assertThat(JsonPath.<String>read(retry.body(), "$.data.tokenValue")).isNotBlank();

        HttpResponse<String> reuse = post("/auth/third/bind",
                "{\"bindTicketId\":\"" + bindTicket + "\",\"username\":\"victim\",\"password\":\"" + PASSWORD
                        + "\"}", null);
        assertThat(reuse.statusCode()).as(reuse.body()).isEqualTo(400);
        assertThat(reuse.body()).contains("绑定凭证已过期或不存在");
    }

    @Test
    void restrictedAccountCannotLoginByThirdCallback() throws Exception {
        createUser("restricted-third");
        fakeProvider.nextUser(userOf("open-400", "union-400"));
        HttpResponse<String> callback = get("/auth/third/callback/feishu?code=code-d&state=" + stateOf(), null);
        String bindTicket = JsonPath.<String>read(callback.body(), "$.data.bindTicketId");
        HttpResponse<String> bind = post("/auth/third/bind",
                "{\"bindTicketId\":\"" + bindTicket + "\",\"username\":\"restricted-third\",\"password\":\""
                        + PASSWORD + "\"}", null);
        assertThat(bind.statusCode()).as(bind.body()).isEqualTo(200);

        registry.restrict("restricted-third", java.util.List.of("该账号已被禁止登录"));

        HttpResponse<String> second = get("/auth/third/callback/feishu?code=code-e&state=" + stateOf(), null);
        assertThat(second.statusCode()).as(second.body()).isEqualTo(400);
        assertThat(second.body()).contains("该账号已被禁止登录");
    }

    @Test
    void platformsEndpointListsFeishuOnly() throws Exception {
        HttpResponse<String> platforms = get("/auth/third/platforms", null);
        assertThat(platforms.statusCode()).as(platforms.body()).isEqualTo(200);
        assertThat(platforms.body()).contains("feishu");
        assertThat(platforms.body()).contains("飞书");
        assertThat(platforms.body()).doesNotContain("wechat");
        assertThat(platforms.body()).doesNotContain("dingtalk");
    }

    @Test
    void pausedPlatformsRejectedOnLoginEntries() throws Exception {
        HttpResponse<String> authorize = get("/auth/third/wechat/authorize-url", null);
        assertThat(authorize.statusCode()).as(authorize.body()).isEqualTo(400);
        assertThat(authorize.body()).contains("不支持的第三方平台");
        assertThat(authorize.body()).contains("feishu");

        HttpResponse<String> callback = get("/auth/third/callback/dingtalk?code=c&state=s", null);
        assertThat(callback.statusCode()).as(callback.body()).isEqualTo(400);
        assertThat(callback.body()).contains("不支持的第三方平台");
    }

    @Test
    void feishuQrLoginCreatesTicketThenExchangeIssuesBindTicket() throws Exception {
        createUser("qruser");
        fakeProvider.nextUser(userOf("open-qr-1", "union-qr-1"));

        HttpResponse<String> create = post("/auth/third/feishu/qr", "{}", null);
        assertThat(create.statusCode()).as(create.body()).isEqualTo(200);
        String ticketId = JsonPath.<String>read(create.body(), "$.data.ticketId");
        assertThat(JsonPath.<String>read(create.body(), "$.data.content")).contains(ticketId);
        assertThat(JsonPath.<String>read(create.body(), "$.data.imageBase64"))
                .startsWith("data:image/png;base64,");
        assertThat(qrStatusOf(ticketId)).isEqualTo("WAITING");

        HttpResponse<String> early = post("/auth/third/feishu/qr/" + ticketId + "/exchange",
                "{\"deviceType\":\"PC\",\"deviceId\":\"qr-pc\"}", null);
        assertThat(early.statusCode()).as(early.body()).isEqualTo(400);
        assertThat(early.body()).contains("尚未确认");
        assertThat(qrStatusOf(ticketId)).isEqualTo("WAITING");

        // 模拟手机在飞书完成授权：回调携带 state=票据号
        HttpResponse<String> callback = get("/auth/third/callback/feishu?code=qr-code&state=" + ticketId, null);
        assertThat(callback.statusCode()).as(callback.body()).isEqualTo(200);
        assertThat(qrStatusOf(ticketId)).isEqualTo("CONFIRMED");

        HttpResponse<String> exchange = post("/auth/third/feishu/qr/" + ticketId + "/exchange",
                "{\"deviceType\":\"PC\",\"deviceId\":\"qr-pc\"}", null);
        assertThat(exchange.statusCode()).as(exchange.body()).isEqualTo(200);
        assertThat((Boolean) JsonPath.<Boolean>read(exchange.body(), "$.data.needBind")).isTrue();
        String bindTicket = JsonPath.<String>read(exchange.body(), "$.data.bindTicketId");
        assertThat(bindTicket).isNotBlank();

        HttpResponse<String> replay = post("/auth/third/feishu/qr/" + ticketId + "/exchange",
                "{\"deviceType\":\"PC\",\"deviceId\":\"qr-pc\"}", null);
        assertThat(replay.statusCode()).as(replay.body()).isEqualTo(400);
        assertThat(replay.body()).contains("已过期或不存在");

        HttpResponse<String> bind = post("/auth/third/bind",
                "{\"bindTicketId\":\"" + bindTicket + "\",\"username\":\"qruser\",\"password\":\"" + PASSWORD
                        + "\"}", null);
        assertThat(bind.statusCode()).as(bind.body()).isEqualTo(200);
        assertThat(get("/auth/test/me", JsonPath.<String>read(bind.body(), "$.data.tokenValue")).statusCode())
                .isEqualTo(200);
    }

    @Test
    void boundUserQrExchangeEstablishesSessionDirectly() throws Exception {
        createUser("qrbound");
        fakeProvider.nextUser(userOf("open-qr-2", "union-qr-2"));
        HttpResponse<String> first = get("/auth/third/callback/feishu?code=code-bind&state=" + stateOf(), null);
        String bindTicket = JsonPath.<String>read(first.body(), "$.data.bindTicketId");
        assertThat(post("/auth/third/bind",
                "{\"bindTicketId\":\"" + bindTicket + "\",\"username\":\"qrbound\",\"password\":\"" + PASSWORD
                        + "\"}", null).statusCode()).isEqualTo(200);

        HttpResponse<String> create = post("/auth/third/feishu/qr", "{}", null);
        String ticketId = JsonPath.<String>read(create.body(), "$.data.ticketId");
        assertThat(get("/auth/third/callback/feishu?code=code-qr-2&state=" + ticketId, null).statusCode())
                .isEqualTo(200);

        HttpResponse<String> exchange = post("/auth/third/feishu/qr/" + ticketId + "/exchange",
                "{\"deviceType\":\"PC\",\"deviceId\":\"qr-pc-2\"}", null);
        assertThat(exchange.statusCode()).as(exchange.body()).isEqualTo(200);
        assertThat((Boolean) JsonPath.<Boolean>read(exchange.body(), "$.data.needBind")).isFalse();
        String token = JsonPath.<String>read(exchange.body(), "$.data.login.tokenValue");
        assertThat(get("/auth/test/me", token).statusCode()).isEqualTo(200);
    }

    @Test
    void qrEndpointsRejectUnknownPlatformAndMissingTicket() throws Exception {
        HttpResponse<String> unknown = post("/auth/third/wechat/qr", "{}", null);
        assertThat(unknown.statusCode()).as(unknown.body()).isEqualTo(400);
        assertThat(unknown.body()).contains("不支持的第三方平台");

        HttpResponse<String> missing = get("/auth/third/feishu/qr/not-exist-ticket", null);
        assertThat(missing.statusCode()).as(missing.body()).isEqualTo(200);
        assertThat(missing.body()).contains("EXPIRED");
        assertThat(missing.body()).contains("已过期");

        HttpResponse<String> exchange = post("/auth/third/feishu/qr/not-exist-ticket/exchange", "{}", null);
        assertThat(exchange.statusCode()).as(exchange.body()).isEqualTo(400);
        assertThat(exchange.body()).contains("已过期或不存在");
    }

    @Test
    void unknownTicketStatusStaysPollableInsteadOfFailing() throws Exception {
        String ticketId = "bogus-status-ticket";
        long now = System.currentTimeMillis();
        redis.opsForValue().set(ThirdAuthKeys.qrTicketKey(ticketId),
                "{\"ticketId\":\"" + ticketId + "\",\"platform\":\"FEISHU\",\"status\":\"BROKEN\",\"createdAt\":"
                        + now + ",\"expireAt\":" + (now + 300_000L) + "}");

        HttpResponse<String> status = get("/auth/third/feishu/qr/" + ticketId, null);
        assertThat(status.statusCode()).as(status.body()).isEqualTo(200);
        assertThat(JsonPath.<String>read(status.body(), "$.data.status")).isEqualTo("BROKEN");
        assertThat(JsonPath.<String>read(status.body(), "$.data.statusLabel")).isEqualTo("未知状态");

        HttpResponse<String> exchange = post("/auth/third/feishu/qr/" + ticketId + "/exchange", "{}", null);
        assertThat(exchange.statusCode()).as(exchange.body()).isEqualTo(400);
        assertThat(exchange.body()).contains("尚未确认");
    }

    private String qrStatusOf(String ticketId) throws Exception {
        HttpResponse<String> status = get("/auth/third/feishu/qr/" + ticketId, null);
        assertThat(status.statusCode()).as(status.body()).isEqualTo(200);
        return JsonPath.<String>read(status.body(), "$.data.status");
    }

    private ThirdPartyUser userOf(String openId, String unionId) {
        ThirdPartyUser user = new ThirdPartyUser();
        user.setPlatform(ThirdPartyPlatform.FEISHU);
        user.setOpenId(openId);
        user.setUnionId(unionId);
        user.setNickname("飞书昵称" + openId);
        user.setAvatar("https://cdn.example.com/" + openId + ".png");
        return user;
    }

    private String stateOf() throws Exception {
        HttpResponse<String> authorize = get("/auth/third/feishu/authorize-url", null);
        assertThat(authorize.statusCode()).as(authorize.body()).isEqualTo(200);
        return JsonPath.<String>read(authorize.body(), "$.data.state");
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (token != null) {
            builder.header("satoken", token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header("satoken", token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
