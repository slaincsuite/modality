package cn.slainc.modules.infra.recycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

// 回收站：公告删除进回收站、恢复、彻底删除与过期清理
class RecycleModuleTest extends cn.slainc.modules.infra.InfraModuleTestBase {

    @Test
    void noticeDeletedIntoRecycleBinThenRestoredAndPurged() throws Exception {
        String noticeId = post("/infra/notice", "{\"title\":\"下线公告\",\"content\":\"旧功能下线\"}",
                "$.data.id");

        send("DELETE", "/infra/notice/" + noticeId, null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_infra_notice", Integer.class)).isZero();

        String page = get("/infra/recycle?entityType=INFRA_NOTICE");
        assertThat(page).contains("下线公告");
        String itemId = JsonPath.read(page, "$.data.records[0].id").toString();
        assertThat((Boolean) JsonPath.read(page, "$.data.records[0].restored")).isFalse();

        post("/infra/recycle/" + itemId + "/restore", null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_infra_notice", Integer.class)).isEqualTo(1);
        assertThat((Boolean) JsonPath.read(get("/infra/recycle/" + itemId), "$.data.restored")).isTrue();

        // 已恢复的记录不能重复恢复
        var again = send("POST", "/infra/recycle/" + itemId + "/restore", null);
        assertThat(again.statusCode()).isEqualTo(400);
        assertThat(again.body()).contains("记录已恢复");

        send("DELETE", "/infra/recycle/" + itemId, null);
        var missing = send("GET", "/infra/recycle/" + itemId, null);
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(missing.body()).contains("回收站记录不存在");
    }

    @Test
    void restoreRejectsMissingItemAndCleanSweepsExpired() throws Exception {
        var missing = send("GET", "/infra/recycle/999999", null);
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(missing.body()).contains("回收站记录不存在");

        String cleaned = post("/infra/recycle/clean", null);
        assertThat(JsonPath.read(cleaned, "$.data").toString()).isEqualTo("0");
    }

    @Test
    void restoredNoticeKeepsItsOriginalFields() throws Exception {
        String noticeId = post("/infra/notice", "{\"title\":\"恢复公告\",\"content\":\"恢复正文\","
                + "\"level\":\"WARN\"}", "$.data.id");
        send("POST", "/infra/notice/" + noticeId + "/publish", null);

        send("DELETE", "/infra/notice/" + noticeId, null);
        String itemId = JsonPath.read(get("/infra/recycle?entityType=INFRA_NOTICE"), "$.data.records[0].id")
                .toString();
        post("/infra/recycle/" + itemId + "/restore", null);

        String row = jdbc.queryForObject(
                "SELECT CONCAT(title, '|', level, '|', status) FROM t_infra_notice WHERE id = " + noticeId,
                String.class);
        assertThat(row).isEqualTo("恢复公告|WARN|PUBLISHED");
    }
}
