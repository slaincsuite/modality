package cn.slainc.modules.auth.qrcode;

import cn.slainc.core.context.TenantContext;
import cn.slainc.modules.auth.AuthModuleApplication;
import cn.slainc.modules.auth.TestAccountRegistry;
import cn.slainc.security.spi.PasswordAccount;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AuthModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QrLoginModuleTest {

    @Autowired
    private TestAccountRegistry registry;

    @Autowired
    private StringRedisTemplate redis;

    @LocalServerPort
    private int port;

    private HttpClient http;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        for (String prefix : new String[]{"satoken:", "modality:security:", "modality:auth:"}) {
            var keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Test
    void qrFullChainEstablishesSessionOnExchange() throws Exception {
        PasswordAccount account = registry.create("scanner");
        String mobileToken = login("scanner", "mobile-1");

        HttpResponse<String> create = post("/auth/qrcode", "{}", null);
        assertThat(create.statusCode()).as(create.body()).isEqualTo(200);
        String ticketId = JsonPath.<String>read(create.body(), "$.data.ticketId");
        String content = JsonPath.<String>read(create.body(), "$.data.content");
        String image = JsonPath.<String>read(create.body(), "$.data.imageBase64");
        assertThat(content).contains(ticketId);
        String prefix = "data:image/png;base64,";
        assertThat(image).startsWith(prefix);
        byte[] png = Base64.getDecoder().decode(image.substring(prefix.length()));
        assertThat(png.length).isGreaterThan(200);
        assertThat(new String(new byte[]{png[1], png[2], png[3]}, "US-ASCII")).isEqualTo("PNG");

        assertThat(status(ticketId)).isEqualTo("WAITING");

        assertThat(post("/auth/qrcode/" + ticketId + "/scan", "{}", mobileToken).statusCode()).isEqualTo(200);
        assertThat(status(ticketId)).isEqualTo("SCANNED");

        assertThat(post("/auth/qrcode/" + ticketId + "/confirm", "{}", mobileToken).statusCode()).isEqualTo(200);
        assertThat(status(ticketId)).isEqualTo("CONFIRMED");

        HttpResponse<String> exchange = post("/auth/qrcode/" + ticketId + "/exchange",
                "{\"deviceType\":\"PC\",\"deviceId\":\"pc-1\"}", null);
        assertThat(exchange.statusCode()).as(exchange.body()).isEqualTo(200);
        String pcToken = JsonPath.<String>read(exchange.body(), "$.data.tokenValue");
        assertThat(pcToken).isNotBlank();

        HttpResponse<String> me = get("/auth/test/me", pcToken);
        assertThat(me.statusCode()).as(me.body()).isEqualTo(200);
        assertThat(me.body()).contains(String.valueOf(account.userId()));
    }

    @Test
    void exchangeIsSingleUse() throws Exception {
        registry.create("once");
        String mobileToken = login("once", "mobile-2");
        String ticketId = createTicket();
        post("/auth/qrcode/" + ticketId + "/scan", "{}", mobileToken);
        post("/auth/qrcode/" + ticketId + "/confirm", "{}", mobileToken);

        assertThat(post("/auth/qrcode/" + ticketId + "/exchange", "{}", null).statusCode()).isEqualTo(200);
        HttpResponse<String> second = post("/auth/qrcode/" + ticketId + "/exchange", "{}", null);
        assertThat(second.statusCode()).as(second.body()).isEqualTo(400);
        assertThat(second.body()).contains("过期或不存在");
    }

    @Test
    void onlyScanningUserCanConfirm() throws Exception {
        registry.create("scannerA");
        registry.create("scannerB");
        String tokenA = login("scannerA", "mobile-3");
        String tokenB = login("scannerB", "mobile-4");
        String ticketId = createTicket();
        post("/auth/qrcode/" + ticketId + "/scan", "{}", tokenA);

        HttpResponse<String> wrongUser = post("/auth/qrcode/" + ticketId + "/confirm", "{}", tokenB);
        assertThat(wrongUser.statusCode()).as(wrongUser.body()).isEqualTo(400);
        assertThat(wrongUser.body()).contains("仅扫码用户本人");

        assertThat(post("/auth/qrcode/" + ticketId + "/confirm", "{}", tokenA).statusCode()).isEqualTo(200);
    }

    @Test
    void cancelStopsExchange() throws Exception {
        registry.create("canceller");
        String token = login("canceller", "mobile-5");
        String ticketId = createTicket();
        post("/auth/qrcode/" + ticketId + "/scan", "{}", token);
        assertThat(post("/auth/qrcode/" + ticketId + "/cancel", "{}", token).statusCode()).isEqualTo(200);
        assertThat(status(ticketId)).isEqualTo("CANCELLED");

        HttpResponse<String> exchange = post("/auth/qrcode/" + ticketId + "/exchange", "{}", null);
        assertThat(exchange.statusCode()).as(exchange.body()).isEqualTo(400);
        assertThat(exchange.body()).contains("尚未确认");
    }

    @Test
    void refreshInvalidatesOldTicket() throws Exception {
        String oldTicket = createTicket();
        HttpResponse<String> refresh = post("/auth/qrcode/" + oldTicket + "/refresh", "{}", null);
        assertThat(refresh.statusCode()).as(refresh.body()).isEqualTo(200);
        String newTicket = JsonPath.<String>read(refresh.body(), "$.data.ticketId");
        assertThat(newTicket).isNotEqualTo(oldTicket);
        assertThat(status(oldTicket)).isEqualTo("EXPIRED");
        assertThat(status(newTicket)).isEqualTo("WAITING");
    }

    @Test
    void statusHidesUsernameBeforeConfirm() throws Exception {
        registry.create("hider");
        String mobileToken = login("hider", "mobile-hide");
        String ticketId = createTicket();
        assertThat(post("/auth/qrcode/" + ticketId + "/scan", "{}", mobileToken).statusCode()).isEqualTo(200);

        HttpResponse<String> scanned = get("/auth/qrcode/" + ticketId, null);
        assertThat(scanned.body()).contains("SCANNED");
        assertThat(scanned.body()).doesNotContain("hider");
        assertThat(JsonPath.<String>read(scanned.body(), "$.data.scannedUsername")).isNull();

        assertThat(post("/auth/qrcode/" + ticketId + "/confirm", "{}", mobileToken).statusCode()).isEqualTo(200);
        assertThat(get("/auth/qrcode/" + ticketId, null).body()).contains("hider");
    }

    @Test
    void restrictedAccountCannotEstablishSessionByQr() throws Exception {
        registry.create("restricted-qr");
        String mobileToken = login("restricted-qr", "mobile-6");
        String ticketId = createTicket();
        post("/auth/qrcode/" + ticketId + "/scan", "{}", mobileToken);
        post("/auth/qrcode/" + ticketId + "/confirm", "{}", mobileToken);

        registry.restrict("restricted-qr", java.util.List.of("该账号已被禁止扫码登录"));

        HttpResponse<String> exchange = post("/auth/qrcode/" + ticketId + "/exchange",
                "{\"deviceType\":\"PC\"}", null);
        assertThat(exchange.statusCode()).as(exchange.body()).isEqualTo(400);
        assertThat(exchange.body()).contains("该账号已被禁止扫码登录");
    }

    private String createTicket() throws Exception {
        HttpResponse<String> create = post("/auth/qrcode", "{}", null);
        assertThat(create.statusCode()).as(create.body()).isEqualTo(200);
        return JsonPath.<String>read(create.body(), "$.data.ticketId");
    }

    private String status(String ticketId) throws Exception {
        HttpResponse<String> response = get("/auth/qrcode/" + ticketId, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.<String>read(response.body(), "$.data.status");
    }

    private String login(String username, String deviceId) throws Exception {
        HttpResponse<String> response = post("/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + TestAccountRegistry.PASSWORD
                        + "\",\"deviceId\":\"" + deviceId + "\"}", null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.<String>read(response.body(), "$.data.tokenValue");
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
                .header("X-Login-Type", "TENANT_USER")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header("satoken", token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
