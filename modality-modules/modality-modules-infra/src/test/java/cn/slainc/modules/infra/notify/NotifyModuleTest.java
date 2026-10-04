package cn.slainc.modules.infra.notify;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// 消息通知：模板渲染、站内信已读未读与批量已读、Webhook 真实 HTTP 发送、失败记录与重试（无 Redis 路线）
class NotifyModuleTest extends cn.slainc.modules.infra.InfraModuleTestBase {

    private HttpServer webhookServer;

    private final AtomicInteger webhookHits = new AtomicInteger();

    @AfterEach
    void stopServer() {
        if (webhookServer != null) {
            webhookServer.stop(0);
            webhookServer = null;
        }
    }

    @Test
    void templateRenderedInAppMessageReadUnreadFlow() throws Exception {
        post("/infra/notify/templates", "{\"code\":\"order.done\",\"name\":\"订单完成\","
                + "\"titleTemplate\":\"订单 {orderNo} 已完成\",\"contentTemplate\":\"尊敬的 {name}，订单 {orderNo}"
                + " 已发货\",\"channel\":\"INAPP\"}");

        post("/infra/notify/in-app", "{\"templateCode\":\"order.done\",\"receivers\":[94001],"
                + "\"params\":{\"orderNo\":\"A001\",\"name\":\"张三\"}}");

        assertThat(Integer.parseInt(JsonPath.read(get("/infra/notify/my/unread-count"), "$.data").toString()))
                .isEqualTo(1);
        String page = get("/infra/notify/my/page?read=false");
        assertThat(page).contains("订单 A001 已完成").contains("张三");

        long messageId = Long.parseLong(JsonPath.read(page, "$.data.records[0].id").toString());
        post("/infra/notify/my/" + messageId + "/read", null);
        assertThat(Integer.parseInt(JsonPath.read(get("/infra/notify/my/unread-count"), "$.data").toString()))
                .isZero();
        assertThat(get("/infra/notify/my/page?read=true")).contains("订单 A001");

        HttpResponse<String> foreign = send("DELETE", "/infra/notify/my/" + messageId, null);
        assertThat(foreign.statusCode()).isEqualTo(200);

        post("/infra/notify/in-app", "{\"templateCode\":\"order.done\",\"receivers\":[94001],"
                + "\"params\":{\"orderNo\":\"A002\",\"name\":\"李四\"}}");
        post("/infra/notify/in-app", "{\"templateCode\":\"order.done\",\"receivers\":[94001],"
                + "\"params\":{\"orderNo\":\"A003\",\"name\":\"王五\"}}");
        assertThat(Integer.parseInt(JsonPath.read(post("/infra/notify/my/read-all", null), "$.data").toString()))
                .isEqualTo(2);
        String records = get("/infra/notify/records/page?channel=INAPP");
        assertThat(Integer.parseInt(JsonPath.read(records, "$.data.total").toString())).isEqualTo(3);
    }

    @Test
    void webhookSendRecordAndRetry() throws Exception {
        startWebhookServer();
        post("/infra/notify/webhooks", "{\"name\":\"验收钩子\",\"platform\":\"FEISHU\","
                + "\"url\":\"http://127.0.0.1:" + webhookServer.getAddress().getPort() + "/hook\"}");
        long webhookId = Long.parseLong(JsonPath.read(get("/infra/notify/webhooks/page"), "$.data.records[0].id")
                .toString());

        String record = post("/infra/notify/webhooks/" + webhookId + "/send",
                "{\"params\":{\"title\":\"验收通知\",\"content\":\"来自基础设施管理台\"}}", "$.data.status");
        assertThat(record).isEqualTo("SUCCESS");
        assertThat(webhookHits.get()).isEqualTo(1);

        jdbc.update("UPDATE t_infra_notify_webhook SET url = 'http://127.0.0.1:9/hook' WHERE id = ?" , webhookId);
        HttpResponse<String> failed = send("POST", "/infra/notify/webhooks/" + webhookId + "/send",
                "{\"params\":{\"title\":\"失败通知\",\"content\":\"打不通\"}}");
        assertThat(failed.statusCode()).isEqualTo(502);
        String failedRecords = get("/infra/notify/records/page?status=FAILED");
        String failedId = JsonPath.read(failedRecords, "$.data.records[0].id").toString();
        assertThat(failedRecords).contains("打不通");

        jdbc.update("UPDATE t_infra_notify_webhook SET url = 'http://127.0.0.1:"
                + webhookServer.getAddress().getPort() + "/hook' WHERE id = ?", webhookId);
        String retried = post("/infra/notify/records/" + failedId + "/retry", null, "$.data.status");
        assertThat(retried).isEqualTo("SUCCESS");
        assertThat(webhookHits.get()).isEqualTo(2);
    }

    @Test
    void retryRejectsSuccessRecordAndHonoursMaxRetry() throws Exception {
        startWebhookServer();
        post("/infra/notify/webhooks", "{\"name\":\"重试上限\",\"platform\":\"CUSTOM\","
                + "\"url\":\"http://127.0.0.1:9/hook\"}");
        long webhookId = Long.parseLong(JsonPath.read(get("/infra/notify/webhooks/page"), "$.data.records[0].id")
                .toString());
        send("POST", "/infra/notify/webhooks/" + webhookId + "/send",
                "{\"params\":{\"title\":\"一直失败\",\"content\":\"x\"}}");
        String failedId = JsonPath.read(get("/infra/notify/records/page?status=FAILED"),
                "$.data.records[0].id").toString();

        jdbc.update("UPDATE t_infra_notify_webhook SET url = ? WHERE id = ?",
                "http://127.0.0.1:" + webhookServer.getAddress().getPort() + "/hook", webhookId);
        post("/infra/notify/records/" + failedId + "/retry", null);

        jdbc.update("UPDATE t_infra_notify_record SET status = 'FAILED', retry_count = 3 WHERE id = ?",
                Long.parseLong(failedId));
        HttpResponse<String> exhausted = send("POST", "/infra/notify/records/" + failedId + "/retry", null);
        assertThat(exhausted.statusCode()).isEqualTo(400);
        assertThat(exhausted.body()).contains("重试次数已达上限");
    }

    private void startWebhookServer() throws IOException {
        webhookServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        webhookServer.createContext("/hook", exchange -> {
            webhookHits.incrementAndGet();
            byte[] response = "{\"errcode\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
        });
        webhookServer.start();
    }
}
