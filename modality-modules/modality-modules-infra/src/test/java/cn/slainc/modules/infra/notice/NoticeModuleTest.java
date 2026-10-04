package cn.slainc.modules.infra.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

// 系统公告：草稿编辑限制、发布/下架、生效窗口（无 Redis 路线）
class NoticeModuleTest extends cn.slainc.modules.infra.InfraModuleTestBase {

    @Test
    void draftEditPublishOfflineLifecycle() throws Exception {
        String id = post("/infra/notice",
                "{\"title\":\"升级公告\",\"content\":\"今晚 22 点升级\",\"level\":\"WARN\"}", "$.data.id");

        HttpResponse<String> published = send("POST", "/infra/notice/" + id + "/publish", null);
        assertThat(published.statusCode()).isEqualTo(200);
        assertThat(JsonPath.read(published.body(), "$.data.status").toString()).isEqualTo("PUBLISHED");

        HttpResponse<String> editAfterPublish = send("PUT", "/infra/notice/" + id,
                "{\"title\":\"改标题\"}");
        assertThat(editAfterPublish.statusCode()).isEqualTo(400);
        assertThat(editAfterPublish.body()).contains("仅草稿状态的公告可以编辑");

        send("POST", "/infra/notice/" + id + "/offline", null);
        String active = get("/infra/notice/active");
        assertThat(active).doesNotContain("升级公告");

        send("POST", "/infra/notice/" + id + "/publish", null);
        assertThat(get("/infra/notice/active")).contains("升级公告");
    }

    @Test
    void expiredNoticeHiddenFromActiveList() throws Exception {
        long expireAt = System.currentTimeMillis() - 60000;
        String id = post("/infra/notice", "{\"title\":\"过期公告\",\"content\":\"已过期\",\"expireTime\":"
                + expireAt + "}", "$.data.id");
        send("POST", "/infra/notice/" + id + "/publish", null);
        String active = get("/infra/notice/active");
        assertThat(active).doesNotContain("过期公告");

        String page = get("/infra/notice/page?status=PUBLISHED");
        assertThat(page).contains("过期公告");
    }

    @Test
    void levelValidated() throws Exception {
        HttpResponse<String> bad = send("POST", "/infra/notice",
                "{\"title\":\"级别错\",\"content\":\"x\",\"level\":\"EXTREME\"}");
        assertThat(bad.statusCode()).isEqualTo(400);
        assertThat(bad.body()).contains("公告级别只允许");
    }
}
