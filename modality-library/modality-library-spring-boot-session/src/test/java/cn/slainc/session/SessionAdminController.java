package cn.slainc.session;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.result.Result;
import cn.slainc.security.annotation.RequireLogin;
import cn.slainc.session.cleanup.CleanupReport;
import cn.slainc.session.cleanup.SessionCleanupService;
import cn.slainc.session.extend.SessionExtender;
import cn.slainc.session.extend.SessionRenewalService;
import cn.slainc.session.kick.KickSessionService;
import cn.slainc.session.online.OnlineUserService;
import cn.slainc.session.query.SessionQueryService;
import cn.slainc.session.stats.SessionStats;
import cn.slainc.session.stats.SessionStatsService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequireLogin(LoginType.ADMIN)
public class SessionAdminController {

    private final OnlineUserService onlineUserService;

    private final SessionQueryService queryService;

    private final KickSessionService kickSessionService;

    private final SessionStatsService statsService;

    private final SessionExtender extender;

    private final SessionRenewalService renewalService;

    private final ObjectProvider<SessionCleanupService> cleanupService;

    public SessionAdminController(OnlineUserService onlineUserService, SessionQueryService queryService,
                                  KickSessionService kickSessionService, SessionStatsService statsService,
                                  SessionExtender extender, SessionRenewalService renewalService,
                                  ObjectProvider<SessionCleanupService> cleanupService) {
        this.onlineUserService = onlineUserService;
        this.queryService = queryService;
        this.kickSessionService = kickSessionService;
        this.statsService = statsService;
        this.extender = extender;
        this.renewalService = renewalService;
        this.cleanupService = cleanupService;
    }

    @GetMapping("/session/online")
    public Result<OnlineUserPage> online(@RequestParam(required = false) String type,
                                         @RequestParam(required = false) String tenantId,
                                         @RequestParam(required = false) String deviceType,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         @RequestParam(required = false) String sort,
                                         @RequestParam(defaultValue = "true") boolean desc,
                                         @RequestParam(required = false) Long loginAfter,
                                         @RequestParam(required = false) Long loginBefore) {
        OnlineQuery query = OnlineQuery.builder()
                .type(LoginType.of(type))
                .tenantId(tenantId)
                .deviceType(deviceType)
                .keyword(keyword)
                .page(page)
                .size(size)
                .sortField(sort == null ? SessionSortField.LOGIN_TIME : SessionSortField.valueOf(sort))
                .descending(desc)
                .loginAfter(toTime(loginAfter))
                .loginBefore(toTime(loginBefore))
                .build();
        return Result.ok(onlineUserService.pageWithSummary(query));
    }

    @GetMapping(value = "/session/export.csv", produces = "text/csv;charset=UTF-8")
    public String export(@RequestParam(required = false) String type,
                         @RequestParam(required = false) String tenantId,
                         @RequestParam(required = false) String deviceType,
                         @RequestParam(required = false) String keyword) {
        OnlineQuery query = OnlineQuery.builder().type(LoginType.of(type)).tenantId(tenantId)
                .deviceType(deviceType).keyword(keyword).build();
        return onlineUserService.exportCsv(query);
    }

    @GetMapping("/session/detail")
    public Result<SessionInfo> detail(@RequestParam String type, @RequestParam String token) {
        return Result.ok(queryService.detail(LoginType.of(type), token));
    }

    @GetMapping("/session/by-device")
    public Result<List<SessionInfo>> byDevice(@RequestParam(required = false) String type,
                                              @RequestParam String deviceId) {
        return Result.ok(queryService.byDeviceId(LoginType.of(type), deviceId));
    }

    @GetMapping("/session/count")
    public Result<Long> count(@RequestParam(required = false) String type) {
        return Result.ok(onlineUserService.onlineCount(LoginType.of(type)));
    }

    @GetMapping("/session/stats")
    public Result<Map<String, Object>> stats(@RequestParam(required = false) String type) {
        SessionStats stats = statsService.snapshot(LoginType.of(type));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", stats.total());
        body.put("byType", stats.byType());
        body.put("byDeviceType", stats.byDeviceType());
        body.put("byTenant", namedTenants(stats.byTenant()));
        body.put("avgDurationSeconds", stats.avgDurationSeconds());
        body.put("truncated", stats.truncated());
        body.put("message", stats.message());
        return Result.ok(body);
    }

    @GetMapping("/session/count-by-tenant")
    public Result<Map<String, Long>> countByTenant() {
        return Result.ok(namedTenants(statsService.countByTenant()));
    }

    private Map<String, Long> namedTenants(Map<String, Long> source) {
        Map<String, Long> named = new LinkedHashMap<>();
        source.forEach((key, value) -> named.put(key == null ? "未归属" : key, value));
        return named;
    }

    @PostMapping("/session/kick")
    public Result<Integer> kick(@RequestParam String type, @RequestParam Long loginId,
                                @RequestParam(required = false) String deviceType,
                                @RequestParam(defaultValue = "管理员强制下线") String reason) {
        if (deviceType == null || deviceType.isBlank()) {
            kickSessionService.kickout(LoginType.of(type), loginId, reason);
        } else {
            kickSessionService.kickout(LoginType.of(type), loginId, deviceType, reason);
        }
        return Result.ok(1);
    }

    @PostMapping("/session/kick/token")
    public Result<Boolean> kickByToken(@RequestParam String type, @RequestParam String token,
                                       @RequestParam(defaultValue = "管理员强制下线") String reason) {
        return Result.ok(kickSessionService.kickoutByToken(LoginType.of(type), token, reason));
    }

    @PostMapping("/session/kick/batch")
    public Result<Integer> batchKick(@RequestParam String type, @RequestParam String loginIds,
                                     @RequestParam(defaultValue = "管理员批量踢人") String reason) {
        List<Long> ids = Arrays.stream(loginIds.split(",")).map(String::trim).filter(item -> !item.isEmpty())
                .map(Long::parseLong).toList();
        return Result.ok(kickSessionService.batchKickout(LoginType.of(type), ids, reason));
    }

    @PostMapping("/session/kick/tenant")
    public Result<Integer> kickByTenant(@RequestParam(required = false) String type,
                                        @RequestParam String tenantId,
                                        @RequestParam(defaultValue = "租户管理员踢人") String reason) {
        return Result.ok(kickSessionService.kickoutByTenant(LoginType.of(type), tenantId, reason));
    }

    @PostMapping("/session/kick/device")
    public Result<Integer> kickByDevice(@RequestParam String type, @RequestParam Long loginId,
                                        @RequestParam String deviceId,
                                        @RequestParam(defaultValue = "设备下线") String reason) {
        return Result.ok(kickSessionService.kickoutByDevice(LoginType.of(type), loginId, deviceId, reason));
    }

    @PostMapping("/session/force-logout")
    public Result<Integer> forceLogout(@RequestParam String type, @RequestParam Long loginId,
                                       @RequestParam(defaultValue = "管理员强制注销") String reason) {
        return Result.ok(kickSessionService.forceLogout(LoginType.of(type), loginId, reason));
    }

    @PostMapping("/session/extra")
    public Result<Boolean> setExtra(@RequestParam String type, @RequestParam String token,
                                    @RequestParam String key, @RequestParam String value) {
        return Result.ok(extender.setExtra(LoginType.of(type), token, key, value));
    }

    @GetMapping("/session/extra")
    public Result<Map<String, Object>> getExtra(@RequestParam String type, @RequestParam String token) {
        return Result.ok(extender.extraData(LoginType.of(type), token));
    }

    @PostMapping("/session/renew")
    public Result<Long> renew(@RequestParam String type, @RequestParam String token,
                              @RequestParam long seconds) {
        return Result.ok(renewalService.renew(LoginType.of(type), token, seconds));
    }

    @PostMapping("/session/cleanup/zombie")
    public Result<CleanupReport> cleanupZombie(@RequestParam long idleSeconds) {
        SessionCleanupService service = cleanupService.getIfAvailable();
        return Result.ok(service == null ? CleanupReport.empty() : service.cleanupZombie(idleSeconds));
    }

    private LocalDateTime toTime(Long millis) {
        return millis == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }
}
