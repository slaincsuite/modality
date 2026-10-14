package cn.slainc.modules.infra.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

// 搜索引擎：文档写入-检索-删除闭环 + 搜索历史自助接口（Lucene 提交为异步，轮询等待可见）
class SearchModuleTest extends cn.slainc.modules.infra.InfraModuleTestBase {

    private static final long AWAIT_MILLIS = 300L;

    private static final int AWAIT_ROUNDS = 30;

    @Test
    void documentIndexSearchAndRemoveLifecycle() throws Exception {
        post("/infra/search/documents", "{\"sourceType\":\"article\",\"sourceId\":\"1\","
                + "\"title\":\"Java 微服务指南\",\"summary\":\"检索集成\",\"content\":\"java spring modality\","
                + "\"tags\":\"java,spring\",\"url\":\"/article/1\"}", "$.data.id");
        post("/infra/search/documents", "{\"sourceType\":\"article\",\"sourceId\":\"2\","
                + "\"title\":\"Python 入门\",\"summary\":\"Python 基础\",\"content\":\"python basics\","
                + "\"tags\":\"python\",\"url\":\"/article/2\"}", "$.data.id");

        assertThat(awaitTotal("java", 1)).isTrue();

        String documents = get("/infra/search/documents?sourceType=article");
        assertThat(documents).contains("Java 微服务指南");
        assertThat(documents).contains("Python 入门");

        String pyOnly = get("/infra/search/documents?keyword=" + encode("Python"));
        assertThat(pyOnly).contains("Python 入门");
        assertThat(pyOnly).doesNotContain("Java 微服务指南");

        String stats = get("/infra/search/stats");
        assertThat((Integer) JsonPath.read(stats, "$.data.documentCount")).isGreaterThanOrEqualTo(2);

        String reindex = post("/infra/search/reindex", "{}");
        assertThat(JsonPath.read(reindex, "$.data").toString()).isEqualTo("0");

        assertThat(send("DELETE", "/infra/search/documents/article/1", null).statusCode()).isEqualTo(200);
        assertThat(awaitTotal("java", 0)).isTrue();
        assertThat(get("/infra/search/documents?sourceType=article")).doesNotContain("Java 微服务指南");
    }

    @Test
    void historyRecordsDedupesAndClearsForCurrentAccount() throws Exception {
        get("/infra/search?keyword=" + encode("modality"));
        get("/infra/search?keyword=" + encode("modality"));
        get("/infra/search?keyword=" + encode("java"));

        String history = get("/infra/search/history?limit=10");
        assertThat(history).contains("modality");
        assertThat(history).contains("java");
        assertThat((Integer) JsonPath.read(history, "$.data.length()")).isEqualTo(2);

        Integer count = JsonPath.read(get("/infra/search/history"), "$.data.length()");
        assertThat(count).isEqualTo(2);

        String cleared = send("DELETE", "/infra/search/history", null).body();
        assertThat((Integer) JsonPath.read(cleared, "$.data")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(get("/infra/search/history"), "$.data.length()")).isZero();
    }

    @Test
    void documentRequiresSourceTypeAndSourceId() throws Exception {
        var response = send("POST", "/infra/search/documents", "{\"title\":\"缺来源\"}");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("来源类型");
    }

    private boolean awaitTotal(String keyword, long expected) throws Exception {
        for (int i = 0; i < AWAIT_ROUNDS; i++) {
            String body = get("/infra/search?keyword=" + encode(keyword));
            if (JsonPath.read(body, "$.data.total") instanceof Number number
                    && number.longValue() == expected) {
                return true;
            }
            Thread.sleep(AWAIT_MILLIS);
        }
        return false;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
