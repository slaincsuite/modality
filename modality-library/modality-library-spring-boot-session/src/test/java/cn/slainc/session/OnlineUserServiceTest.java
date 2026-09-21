package cn.slainc.session;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.result.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OnlineUserServiceTest extends SessionTestSupport {

    @Test
    @DisplayName("在线分页默认按登录时间倒序")
    void pageDefaultsToLoginTimeDesc() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "web");

        PageResult<SessionInfo> page = onlineService.page(OnlineQuery.of(LoginType.TENANT_USER));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.records()).hasSize(2);
        assertThat(page.records().get(0).loginId()).isEqualTo("102");
        assertThat(page.records().get(0).loginTime())
                .isAfterOrEqualTo(page.records().get(1).loginTime());
    }

    @Test
    @DisplayName("在线分页按页码与条数切片")
    void pageSlicing() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "web");
        loginDirect(LoginType.TENANT_USER, USER_THREE, "web");

        PageResult<SessionInfo> first = onlineService.page(OnlineQuery.builder().type(LoginType.TENANT_USER).size(2)
                .page(1).build());
        PageResult<SessionInfo> second = onlineService.page(OnlineQuery.builder().type(LoginType.TENANT_USER).size(2)
                .page(2).build());

        assertThat(first.records()).hasSize(2);
        assertThat(first.total()).isEqualTo(3);
        assertThat(first.pages()).isEqualTo(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.records()).hasSize(1);
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    @DisplayName("在线分页超出页码时返回空记录但保留总数")
    void pageBeyondLastReturnsEmpty() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");

        PageResult<SessionInfo> page = onlineService.page(OnlineQuery.builder().type(LoginType.TENANT_USER).page(9).build());

        assertThat(page.records()).isEmpty();
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("在线分页 size 被限制在最大条数内")
    void pageSizeLimit() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");

        PageResult<SessionInfo> page = onlineService.page(OnlineQuery.builder().type(LoginType.TENANT_USER).size(9999)
                .build());

        assertThat(page.size()).isEqualTo(OnlineQuery.MAX_SIZE);
    }

    @Test
    @DisplayName("账号体系为空时统计全部体系")
    void pageAllTypes() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        loginDirect(LoginType.APP, CLIENT_ONE, "web");
        loginDirect(LoginType.PLATFORM_ADMIN, ADMIN_ONE, "web");

        PageResult<SessionInfo> page = onlineService.page(OnlineQuery.all());

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.records()).extracting(SessionInfo::loginType).contains("PLATFORM_ADMIN", "TENANT_USER", "APP");
    }

    @Test
    @DisplayName("在线分页按登录账号片段过滤")
    void pageFiltersByLoginIdFragment() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "web");

        PageResult<SessionInfo> page = onlineService.page(OnlineQuery.builder().type(LoginType.TENANT_USER).keyword("102")
                .build());

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records().get(0).loginId()).isEqualTo("102");
    }

    @Test
    @DisplayName("在线分页按 token 片段过滤")
    void pageFiltersByTokenFragment() {
        String token = loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "web");

        PageResult<SessionInfo> page = onlineService.page(OnlineQuery.builder().type(LoginType.TENANT_USER)
                .keyword(token.substring(0, 6)).build());

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records().get(0).loginId()).isEqualTo("101");
    }

    @Test
    @DisplayName("在线分页按租户过滤")
    void pageFiltersByTenant() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web", "d1", "127.0.0.1", null, "1001");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "web", "d2", "127.0.0.1", null, "1002");
        loginDirect(LoginType.TENANT_USER, USER_THREE, "web", "d3", "127.0.0.1", null, null);

        assertThat(onlineService.page(OnlineQuery.builder().type(LoginType.TENANT_USER).tenantId("1001").build()).total())
                .isEqualTo(1);
        assertThat(onlineService.page(OnlineQuery.builder().type(LoginType.TENANT_USER).tenantId("").build()).records())
                .extracting(SessionInfo::loginId).containsExactly("103");
    }

    @Test
    @DisplayName("在线分页按设备类型原始值过滤")
    void pageFiltersByRawDeviceType() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "app-ios");

        PageResult<SessionInfo> page = onlineService.page(OnlineQuery.builder().type(LoginType.TENANT_USER)
                .deviceType("app-ios").build());

        assertThat(page.records()).extracting(SessionInfo::loginId).containsExactly("102");
    }

    @Test
    @DisplayName("在线分页按设备类型归一类别过滤")
    void pageFiltersByNormalizedCategory() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "app-android");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "web");
        loginDirect(LoginType.TENANT_USER, USER_THREE, "ipad");

        assertThat(onlineService.page(OnlineQuery.builder().type(LoginType.TENANT_USER).deviceType("MOBILE").build())
                .records()).extracting(SessionInfo::loginId).containsExactly("101");
        assertThat(onlineService.page(OnlineQuery.builder().type(LoginType.TENANT_USER).deviceType("TABLET").build())
                .records()).extracting(SessionInfo::loginId).containsExactly("103");
    }

    @Test
    @DisplayName("在线分页按登录时间区间过滤")
    void pageFiltersByLoginTimeRange() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        LocalDateTime now = LocalDateTime.now();

        assertThat(onlineService.page(OnlineQuery.builder().type(LoginType.TENANT_USER)
                .loginAfter(now.minusMinutes(5)).build()).total()).isEqualTo(1);
        assertThat(onlineService.page(OnlineQuery.builder().type(LoginType.TENANT_USER)
                .loginAfter(now.plusMinutes(5)).build()).total()).isZero();
        assertThat(onlineService.page(OnlineQuery.builder().type(LoginType.TENANT_USER)
                .loginBefore(now.plusMinutes(5)).build()).total()).isEqualTo(1);
        assertThat(onlineService.page(OnlineQuery.builder().type(LoginType.TENANT_USER)
                .loginBefore(now.minusMinutes(5)).build()).total()).isZero();
    }

    @Test
    @DisplayName("在线分页支持按最后活跃时间升序")
    void pageSortsByLastActive() {
        String first = loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "web");
        setLastActive("TENANT_USER", first, System.currentTimeMillis() - 120_000L);

        PageResult<SessionInfo> page = onlineService.page(OnlineQuery.builder().type(LoginType.TENANT_USER)
                .sortField(SessionSortField.LAST_ACTIVE_TIME).descending(false).build());

        assertThat(page.records()).extracting(SessionInfo::loginId).containsExactly("101", "102");
    }

    @Test
    @DisplayName("在线分页支持按账号升序")
    void pageSortsByLoginId() {
        loginDirect(LoginType.TENANT_USER, USER_TWO, "web");
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");

        PageResult<SessionInfo> page = onlineService.page(OnlineQuery.builder().type(LoginType.TENANT_USER)
                .sortField(SessionSortField.LOGIN_ID).descending(false).build());

        assertThat(page.records()).extracting(SessionInfo::loginId).containsExactly("101", "102");
    }

    @Test
    @DisplayName("在线数量按体系统计")
    void onlineCountByType() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        loginDirect(LoginType.TENANT_USER, USER_ONE, "app");
        loginDirect(LoginType.APP, CLIENT_ONE, "web");

        assertThat(onlineService.onlineCount(LoginType.TENANT_USER)).isEqualTo(2);
        assertThat(onlineService.onlineCount(LoginType.APP)).isEqualTo(1);
        assertThat(onlineService.onlineCount(LoginType.PLATFORM_ADMIN)).isZero();
        assertThat(onlineService.onlineCount(null)).isEqualTo(3);
    }

    @Test
    @DisplayName("按设备类型统计在线数量")
    void countByDeviceType() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "app-android");
        loginDirect(LoginType.APP, CLIENT_ONE, "ipad");
        loginDirect(LoginType.APP, CLIENT_TWO, "default");

        Map<String, Long> counts = onlineService.countByDeviceType();

        assertThat(counts).containsEntry("PC", 1L).containsEntry("MOBILE", 1L).containsEntry("TABLET", 1L)
                .containsEntry("UNKNOWN", 1L);
    }

    @Test
    @DisplayName("按租户统计在线数量并为未归属会话保留空键")
    void countByTenant() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web", "d1", "127.0.0.1", null, "1001");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "web", "d2", "127.0.0.1", null, "1001");
        loginDirect(LoginType.TENANT_USER, USER_THREE, "web", "d3", "127.0.0.1", null, null);

        Map<String, Long> counts = onlineService.countByTenant();

        assertThat(counts).containsEntry("1001", 2L);
        assertThat(counts).containsKey(null);
        assertThat(counts.get(null)).isEqualTo(1L);
    }

    @Test
    @DisplayName("CSV 导出带 UTF-8 BOM 与中文表头")
    void exportCsvWithBomAndHeader() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web", "csv-device", "10.1.1.1", "Agent, v1", "1001");

        String csv = onlineService.exportCsv(OnlineQuery.of(LoginType.TENANT_USER));

        assertThat(csv).startsWith("\uFEFF");
        String[] lines = csv.substring(1).split("\n");
        assertThat(lines[0]).isEqualTo("账号体系,登录账号,Token,设备类型,设备分类,设备标识,客户端IP,User-Agent,登录时间,最后活跃时间,剩余有效期(秒)");
        assertThat(lines[1]).startsWith("TENANT_USER,101,");
        assertThat(lines[1]).contains("\"Agent, v1\"");
        assertThat(lines[1]).contains("csv-device");
        assertThat(lines).hasSize(2);
    }

    @Test
    @DisplayName("CSV 导出限制最大行数")
    void exportCsvLimitsRows() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "web");

        String csv = onlineService.exportCsv(OnlineQuery.of(LoginType.TENANT_USER), 1);

        assertThat(csv.substring(1).split("\n")).hasSize(2);
    }

    @Test
    @DisplayName("扫描达到上限时标记截断并给出中文说明")
    void scanLimitTruncationFlag() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "web");
        loginDirect(LoginType.TENANT_USER, USER_THREE, "web");
        config.set("modality.session.max-scan", "1");

        OnlineUserPage result = onlineService.pageWithSummary(OnlineQuery.of(LoginType.TENANT_USER));

        assertThat(result.truncated()).isTrue();
        assertThat(result.message()).contains("扫描已达上限");
        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.maxScan()).isEqualTo(1);
        assertThat(result.page().total()).isEqualTo(1);
    }

    @Test
    @DisplayName("扫描未超上限时不标记截断")
    void noTruncationUnderScanLimit() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        config.set("modality.session.max-scan", "50");

        OnlineUserPage result = onlineService.pageWithSummary(OnlineQuery.of(LoginType.TENANT_USER));

        assertThat(result.truncated()).isFalse();
        assertThat(result.message()).isNull();
        assertThat(result.maxScan()).isEqualTo(50);
    }

    @Test
    @DisplayName("在线列表同时给出体系维度统计")
    void countByType() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        loginDirect(LoginType.PLATFORM_ADMIN, ADMIN_ONE, "web");

        assertThat(onlineService.countByType()).containsEntry("TENANT_USER", 1L).containsEntry("PLATFORM_ADMIN", 1L);
    }

    @Test
    @DisplayName("在线列表过滤结果与 list 便捷方法一致")
    void listAndPageConsistent() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "web");

        List<SessionInfo> list = onlineService.list(OnlineQuery.builder().type(LoginType.TENANT_USER).size(10).build());

        assertThat(list).hasSize(2);
    }
}
