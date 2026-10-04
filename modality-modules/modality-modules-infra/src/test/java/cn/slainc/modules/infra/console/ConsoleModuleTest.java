package cn.slainc.modules.infra.console;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

// SQL 监控台、系统日志与系统监控台（无 Redis 路线）：explain、慢 SQL、日志级别热调、健康、测试告警
class ConsoleModuleTest extends cn.slainc.modules.infra.InfraModuleTestBase {

    @Test
    void sqlExplainStatsAndReadOnlyGuard() throws Exception {
        String explain = post("/infra/sql/explain", "{\"sql\":\"SELECT 1 AS one\"}");
        assertThat(explain).contains("SELECT");

        String stats = get("/infra/sql/stats");
        assertThat(stats).contains("countByType");

        HttpResponse<String> danger = send("POST", "/infra/sql/explain",
                "{\"sql\":\"DELETE FROM t_infra_dict_type\"}");
        assertThat(danger.statusCode()).isEqualTo(400);
        assertThat(danger.body()).contains("只接受 SELECT / WITH");
    }

    @Test
    void logLevelHotAdjustAndQuery() throws Exception {
        putLevel("cn.slainc.modules.infra", "DEBUG");
        assertThat(JsonPath.read(get("/infra/logs/level?logger=cn.slainc.modules.infra"), "$.data").toString())
                .isEqualTo("DEBUG");

        HttpResponse<String> badLevel = send("PUT", "/infra/logs/level",
                "{\"logger\":\"cn.slainc.modules.infra\",\"level\":\"VERBOSE\"}");
        assertThat(badLevel.statusCode()).isEqualTo(400);
        assertThat(badLevel.body()).contains("日志级别只允许");

        putLevel("cn.slainc.modules.infra", "INFO");

        String page = get("/infra/logs/page?current=1&size=5");
        assertThat(page).contains("\"records\":");
        String files = get("/infra/logs/files");
        assertThat(files).contains(".log");
    }

    @Test
    void systemHealthMetricsAndTestAlert() throws Exception {
        String health = get("/infra/system/health");
        assertThat(health).contains("status");

        String metrics = get("/infra/system/metrics");
        assertThat(metrics).contains("metricCount");

        cn.slainc.modules.infra.InfraModuleApplication.RECEIVED_ALERTS.clear();
        int sent = Integer.parseInt(post("/infra/system/alerts/test",
                "{\"level\":\"WARN\",\"message\":\"管理台测试告警\"}", "$.data"));
        assertThat(sent).isGreaterThanOrEqualTo(1);
        assertThat(cn.slainc.modules.infra.InfraModuleApplication.RECEIVED_ALERTS).isNotEmpty();
        assertThat(cn.slainc.modules.infra.InfraModuleApplication.RECEIVED_ALERTS.get(0).message())
                .isEqualTo("管理台测试告警");

        String rules = get("/infra/system/alerts/rules");
        assertThat(rules).contains("rules");
    }

    private void putLevel(String logger, String level) throws Exception {
        HttpResponse<String> response = send("PUT", "/infra/logs/level",
                "{\"logger\":\"" + logger + "\",\"level\":\"" + level + "\"}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }
}
