package cn.slainc.modules.infra.notify;

import static org.assertj.core.api.Assertions.assertThat;

import cn.slainc.modules.infra.InfraModuleTestBase;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotifyCenterModuleTest extends InfraModuleTestBase {

    @Test
    void messageDetailKeywordSearchAndBatchDeleteRespectOwnership() throws Exception {
        post("/infra/notify/templates", "{\"code\":\"order.done\",\"name\":\"订单完成\","
                + "\"titleTemplate\":\"订单 {orderNo} 完成\",\"contentTemplate\":\"订单 {orderNo} 已完成\","
                + "\"channel\":\"INAPP\"}");
        post("/infra/notify/in-app", "{\"templateCode\":\"order.done\",\"receivers\":[94001],"
                + "\"params\":{\"orderNo\":\"A001\"}}");
        post("/infra/notify/in-app", "{\"templateCode\":\"order.done\",\"receivers\":[94001],"
                + "\"params\":{\"orderNo\":\"A002\"}}");
        post("/infra/notify/in-app", "{\"templateCode\":\"order.done\",\"receivers\":[94002],"
                + "\"params\":{\"orderNo\":\"B001\"}}");

        String page = get("/infra/notify/my/page?keyword=A001");
        assertThat((Integer) JsonPath.read(page, "$.data.total")).isEqualTo(1);
        long ownId = Long.parseLong(JsonPath.read(page, "$.data.records[0].id").toString());
        String detail = get("/infra/notify/my/" + ownId);
        assertThat(detail).contains("A001").contains("已完成");

        String foreignToken = loginAs(94002);
        var foreign = sendAs(foreignToken, "GET", "/infra/notify/my/" + ownId, null);
        assertThat(foreign.statusCode()).isEqualTo(400);
        assertThat(foreign.body()).contains("站内信不存在或不属于当前用户");

        token = loginAs(94001);
        List<Number> ids = JsonPath.read(get("/infra/notify/my/page"), "$.data.records[*].id");
        assertThat(ids).hasSize(2);
        String idsParam = ids.stream().map(Number::longValue).map(String::valueOf)
                .reduce((left, right) -> left + "," + right).orElseThrow();
        String deleted = send("DELETE", "/infra/notify/my?ids=" + idsParam, null).body();
        assertThat((Integer) JsonPath.read(deleted, "$.data")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(get("/infra/notify/my/page"), "$.data.total")).isZero();
    }

    @Test
    void receiverLifecycleVerificationPreferencesAndTenantIsolation() throws Exception {
        String created = post("/infra/notify/receivers", "{\"name\":\"张三\",\"type\":\"USER\","
                + "\"accountId\":\"94001\",\"phone\":\"13800138000\",\"email\":\"zhangsan@example.com\","
                + "\"feishuId\":\"ou_123456\"}");
        long id = Long.parseLong(JsonPath.read(created, "$.data.id").toString());
        String page = get("/infra/notify/receivers/page?keyword=94001");
        assertThat((Integer) JsonPath.read(page, "$.data.total")).isEqualTo(1);
        assertThat(page).contains("138****8000").doesNotContain("13800138000");

        String contacts = send("PUT", "/infra/notify/receivers/" + id + "/contacts",
                "{\"phone\":\"13900139000\"}").body();
        assertThat(contacts).contains("139****9000");
        send("PUT", "/infra/notify/receivers/" + id + "/preferences",
                "{\"nightReceive\":false,\"smsMute\":true,\"marketingMute\":true}");

        String verify = post("/infra/notify/receivers/" + id + "/verify", null);
        String verificationToken = JsonPath.read(verify, "$.data.verificationToken");
        assertThat((List<String>) JsonPath.read(verify, "$.data.channels"))
                .containsExactly("PHONE", "EMAIL", "FEISHU");
        String confirmed = post("/infra/notify/receivers/" + id + "/verify",
                "{\"channel\":\"PHONE\",\"token\":\"" + verificationToken + "\"}");
        assertThat((Boolean) JsonPath.read(confirmed, "$.data.verified")).isTrue();

        String afterVerify = get("/infra/notify/receivers/page?keyword=张三");
        assertThat((Boolean) JsonPath.read(afterVerify, "$.data.records[0].phoneVerified")).isTrue();
        assertThat((Boolean) JsonPath.read(afterVerify, "$.data.records[0].emailVerified")).isFalse();
        assertThat((Boolean) JsonPath.read(afterVerify, "$.data.records[0].nightReceive")).isFalse();
        assertThat((Boolean) JsonPath.read(afterVerify, "$.data.records[0].smsMute")).isTrue();

        jdbc.update("INSERT INTO t_infra_notify_receiver (tenant_id, name, receiver_type, account_id,"
                + " phone_verified, email_verified, feishu_verified, night_receive, sms_mute, marketing_mute,"
                + " create_time, update_time) VALUES (2, '他租户', 'USER', 'other', 0, 0, 0, 1, 0, 0, ?, ?)",
                System.currentTimeMillis(), System.currentTimeMillis());
        Long foreignId = jdbc.queryForObject("SELECT id FROM t_infra_notify_receiver WHERE account_id = 'other'",
                Long.class);
        String visible = get("/infra/notify/receivers/page?keyword=他租户");
        assertThat((Integer) JsonPath.read(visible, "$.data.total")).isZero();
        var crossTenantDelete = send("DELETE", "/infra/notify/receivers/" + foreignId, null);
        assertThat(crossTenantDelete.statusCode()).isEqualTo(400);
        assertThat(crossTenantDelete.body()).contains("不存在或不属于当前租户");

        String deleted = send("DELETE", "/infra/notify/receivers/" + id, null).body();
        assertThat((Integer) JsonPath.read(deleted, "$.data")).isEqualTo(1);
    }

    @Test
    void subscriptionsPreferencesAndAssistantFollowStayUserScoped() throws Exception {
        long receiverId = Long.parseLong(JsonPath.read(post("/infra/notify/receivers",
                "{\"name\":\"订阅接收人\",\"type\":\"USER\",\"accountId\":\"sub-1\","
                        + "\"phone\":\"13800138000\"}"), "$.data.id").toString());
        var createdResponse = send("PUT", "/infra/notify/subscriptions",
                "{\"serviceCode\":\"order.done\",\"serviceName\":\"订单服务\","
                        + "\"channels\":[\"INAPP\",\"FEISHU\"],\"receiverId\":" + receiverId + ",\"muted\":false}");
        assertThat(createdResponse.statusCode()).isEqualTo(200);
        long subscriptionId = Long.parseLong(JsonPath.read(createdResponse.body(), "$.data[0].id").toString());

        jdbc.update("INSERT INTO t_infra_notify_record (channel, target, template_code, title, content, status,"
                        + " create_time, sent_time) VALUES ('INAPP', 'user:94001', 'order.done', '订单 A009 完成',"
                        + " '正文', 'SUCCESS', ?, ?)",
                System.currentTimeMillis(), System.currentTimeMillis());
        String list = get("/infra/notify/subscriptions");
        assertThat((Integer) JsonPath.read(list, "$.data.length()")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(list, "$.data[0].messageCount30d")).isEqualTo(1);
        assertThat((String) JsonPath.read(list, "$.data[0].latestTitle")).isEqualTo("订单 A009 完成");
        assertThat((String) JsonPath.read(list, "$.data[0].receiver")).isEqualTo("订阅接收人");
        jdbc.update("INSERT INTO t_infra_notify_subscription (tenant_id, user_id, service_code, service_name,"
                        + " channels, muted, create_time, update_time) VALUES (2, 94001, 'foreign.service',"
                        + " '他租户服务', 'INAPP', 0, ?, ?)",
                System.currentTimeMillis(), System.currentTimeMillis());
        assertThat((Integer) JsonPath.read(get("/infra/notify/subscriptions"), "$.data.length()")).isEqualTo(1);

        var updated = send("PUT", "/infra/notify/subscriptions",
                "[{\"id\":" + subscriptionId + ",\"channels\":[\"EMAIL\"],\"muted\":true}]");
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(updated.body(), "$.data[0].channels[0]")).isEqualTo("EMAIL");
        assertThat((Boolean) JsonPath.read(updated.body(), "$.data[0].muted")).isTrue();

        String followed = post("/infra/notify/subscriptions/assistant/follow", null);
        assertThat((Boolean) JsonPath.read(followed, "$.data.assistantFollowed")).isTrue();
        String preference = send("PUT", "/infra/notify/preferences",
                "{\"onlyUnverified\":true,\"hideMuted\":true}").body();
        assertThat((Boolean) JsonPath.read(preference, "$.data.onlyUnverified")).isTrue();
        assertThat((Boolean) JsonPath.read(preference, "$.data.hideMuted")).isTrue();

        token = loginAs(94002);
        assertThat((Integer) JsonPath.read(get("/infra/notify/subscriptions"), "$.data.length()")).isZero();
    }

    @Test
    void webhookPageReturnsSecuritySummaryAndSupportsKeyword() throws Exception {
        post("/infra/notify/webhooks", "{\"name\":\"生产飞书\",\"platform\":\"FEISHU\","
                + "\"url\":\"http://127.0.0.1:1/hook\",\"secret\":\"top-secret\"}");
        post("/infra/notify/webhooks", "{\"name\":\"测试机器人\",\"platform\":\"CUSTOM\","
                + "\"url\":\"http://127.0.0.1:1/test\"}");

        String page = get("/infra/notify/webhooks/page?keyword=生产");
        assertThat((Integer) JsonPath.read(page, "$.data.total")).isEqualTo(1);
        assertThat(page).contains("\"secretConfigured\":true")
                .contains("\"securityType\":\"HMAC_SHA256\"")
                .contains("\"members\":[]")
                .doesNotContain("top-secret");
    }
}
