package cn.slainc.modules.open.webhook;

import cn.slainc.modules.open.OpenModuleApplication;
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

import static org.assertj.core.api.Assertions.assertThat;

// Webhook 管理端点：订阅 CRUD、事件目录、投递记录分页与手动重投（真实 Tomcat + H2，无 Redis 路线）
@SpringBootTest(classes = OpenModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebhookAdminModuleTest {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .build();

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("DELETE FROM t_webhook_delivery");
        jdbc.execute("DELETE FROM t_webhook_subscription");
        token = JsonPath.read(login(), "$.data");
    }

    @Test
    void subscriptionCrudEventsAndMaskedSecret() throws Exception {
        String events = get("/open/webhook/events");
        assertThat(JsonPath.read(events, "$.data[0].name").toString())
                .isEqualTo("open.apikey.created");

        String created = post("/open/webhook",
                "{\"name\":\"订单回调\",\"url\":\"http://127.0.0.1:1/hook\","
                + "\"secret\":\"whsec_demo\",\"events\":\"open.webhook.*\"}");
        long id = Long.parseLong(JsonPath.read(created, "$.data.id").toString());
        assertThat(created).contains("\"secretMasked\":\"wh******mo\"");
        assertThat(created).doesNotContain("whsec_demo");

        String page = get("/open/webhook/page?name=订单回调");
        assertThat(Integer.parseInt(JsonPath.read(page, "$.data.total").toString())).isEqualTo(1);
        assertThat(JsonPath.read(page, "$.data.records[0].name").toString()).isEqualTo("订单回调");

        put("/open/webhook/" + id, "{\"name\":\"订单回调V2\",\"version\":0}");
        assertThat(JsonPath.read(get("/open/webhook/" + id), "$.data.name").toString())
                .isEqualTo("订单回调V2");

        String deleted = send("DELETE", "/open/webhook/" + id, null).body();
        assertThat(Integer.parseInt(JsonPath.read(deleted, "$.data").toString())).isEqualTo(1);
        assertThat(Integer.parseInt(JsonPath.read(get("/open/webhook/page"),
                "$.data.total").toString())).isZero();
    }

    @Test
    void keywordSearchesNameAndUrlWhileNameStaysCompatible() throws Exception {
        post("/open/webhook", "{\"name\":\"订单回调\",\"url\":\"https://hooks.example.com/order\"}");
        post("/open/webhook", "{\"name\":\"账单回调\",\"url\":\"https://hooks.example.com/billing\"}");

        assertThat(Integer.parseInt(JsonPath.read(get("/open/webhook/page?keyword=订单"),
                "$.data.total").toString())).isEqualTo(1);
        assertThat(Integer.parseInt(JsonPath.read(get("/open/webhook/page?keyword=/billing"),
                "$.data.total").toString())).isEqualTo(1);
        assertThat(Integer.parseInt(JsonPath.read(get("/open/webhook/page?name=账单"),
                "$.data.total").toString())).isEqualTo(1);
        assertThat(Integer.parseInt(JsonPath.read(get("/open/webhook/page?keyword=不存在的地址"),
                "$.data.total").toString())).isZero();
    }

    @Test
    void deliveryPageIsUnifiedAcrossSubscriptions() throws Exception {
        long first = Long.parseLong(JsonPath.read(post("/open/webhook",
                "{\"name\":\"订阅一\",\"url\":\"https://example.com/one\"}"), "$.data.id").toString());
        long second = Long.parseLong(JsonPath.read(post("/open/webhook",
                "{\"name\":\"订阅二\",\"url\":\"https://example.com/two\"}"), "$.data.id").toString());
        jdbc.execute("DELETE FROM t_webhook_delivery");
        insertDelivery(first, "open.apikey.created", "FAILED");
        insertDelivery(first, "open.apikey.rotated", "SUCCESS");
        insertDelivery(second, "open.apikey.created", "FAILED");

        String firstPage = get("/open/webhook/deliveries/page?subscriptionId=" + first
                + "&eventType=open.apikey.created&current=1&size=1");
        assertThat(Integer.parseInt(JsonPath.read(firstPage, "$.data.total").toString())).isEqualTo(1);
        assertThat(JsonPath.read(firstPage, "$.data.records[0].eventType").toString())
                .isEqualTo("open.apikey.created");

        String failed = get("/open/webhook/deliveries/page?status=FAILED&size=20");
        assertThat(Integer.parseInt(JsonPath.read(failed, "$.data.total").toString())).isEqualTo(2);

        String eventType = get("/open/webhook/deliveries/page?eventType=open.apikey.created&size=20");
        assertThat(Integer.parseInt(JsonPath.read(eventType, "$.data.total").toString())).isEqualTo(2);
    }

    @Test
    void manualRetryResetsAndRedeliversAFailedDelivery() throws Exception {
        String created = post("/open/webhook",
                "{\"name\":\"失败回调\",\"url\":\"http://127.0.0.1:1/hook\","
                + "\"secret\":\"whsec_demo\"}");
        long subscriptionId = Long.parseLong(JsonPath.read(created, "$.data.id").toString());
        jdbc.update("INSERT INTO t_webhook_delivery (subscription_id, event_type, payload, status,"
                + " attempt, next_retry_at, created_at) VALUES (?, 'open.webhook.subscription-created',"
                + "'{}', 'EXHAUSTED', 5, ?, ?)", subscriptionId, System.currentTimeMillis(),
                System.currentTimeMillis());
        // 新订阅默认订阅 *，自身创建事件也会排队投递，因此用 EXHAUSTED 精确定位手工插入的那条
        long deliveryId = jdbc.queryForObject(
                "SELECT id FROM t_webhook_delivery WHERE status = 'EXHAUSTED'", Long.class);

        post("/open/webhook/deliveries/" + deliveryId + "/retry", null);

        String status = awaitDeliveryStatus(deliveryId);
        // attempt=5 且 max_retry=5，重投一次后必然进入 EXHAUSTED
        assertThat(status).isEqualTo("EXHAUSTED");
        String deliveries = get("/open/webhook/" + subscriptionId + "/deliveries?status=" + status);
        assertThat(Integer.parseInt(JsonPath.read(deliveries, "$.data.total").toString()))
                .isEqualTo(1);
    }

    @Test
    void retryOfAnUnknownDeliveryReportsChineseError() throws Exception {
        HttpResponse<String> response = send("POST", "/open/webhook/deliveries/999999/retry", null);
        assertThat(response.body()).contains("投递记录不存在");
    }

    private String awaitDeliveryStatus(long deliveryId) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000L;
        String last = "PENDING";
        while (System.currentTimeMillis() < deadline) {
            String status = jdbc.queryForObject(
                    "SELECT status FROM t_webhook_delivery WHERE id = ?", String.class, deliveryId);
            last = status;
            if (!"PENDING".equals(status)) {
                return status;
            }
            Thread.sleep(200L);
        }
        return last;
    }

    private void insertDelivery(long subscriptionId, String eventType, String status) {
        jdbc.update("INSERT INTO t_webhook_delivery (subscription_id, event_type, payload, status,"
                        + " attempt, next_retry_at, created_at) VALUES (?, ?, '{}', ?, 0, ?, ?)",
                subscriptionId, eventType, status, System.currentTimeMillis(), System.currentTimeMillis());
    }

    private String login() throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/open-test/login"))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private String get(String path) throws Exception {
        return send("GET", path, null).body();
    }

    private String post(String path, String body) throws Exception {
        HttpResponse<String> response = send("POST", path, body);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return response.body();
    }

    private void put(String path, String body) throws Exception {
        HttpResponse<String> response = send("PUT", path, body);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Login-Type", "TENANT_USER")
                .header("satoken", token);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
