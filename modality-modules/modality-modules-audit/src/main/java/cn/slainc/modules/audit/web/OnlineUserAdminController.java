package cn.slainc.modules.audit.web;

import cn.slainc.core.enums.LoginType;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLevel;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.audit.AuditKeys;
import cn.slainc.modules.audit.entity.AuditLogRecord;
import cn.slainc.modules.audit.excel.OnlineUserExcelService;
import cn.slainc.modules.audit.service.AuditLogService;
import cn.slainc.security.StpKit;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.session.OnlineQuery;
import cn.slainc.session.OnlineUserPage;
import cn.slainc.session.SessionInfo;
import cn.slainc.session.SessionSortField;
import cn.slainc.session.kick.KickSessionService;
import cn.slainc.session.online.OnlineUserService;
import cn.slainc.session.stats.SessionStats;
import cn.slainc.session.stats.SessionStatsService;
import cn.slainc.web.http.DownloadResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;


@Tag(name = "在线用户管理", description = "在线会话查询与强制下线")
@RestController
@RequestMapping("/audit/online")
public class OnlineUserAdminController {

    private static final String LOGIN_TYPE_HEADER = "X-Login-Type";

    private final OnlineUserService onlineUserService;

    private final KickSessionService kickSessionService;

    private final AuditLogService auditLogService;

    private final OnlineUserExcelService excelService;

    private final SessionStatsService sessionStatsService;

    public OnlineUserAdminController(OnlineUserService onlineUserService, KickSessionService kickSessionService,
                                     AuditLogService auditLogService, OnlineUserExcelService excelService,
                                     SessionStatsService sessionStatsService) {
        this.onlineUserService = onlineUserService;
        this.kickSessionService = kickSessionService;
        this.auditLogService = auditLogService;
        this.excelService = excelService;
        this.sessionStatsService = sessionStatsService;
    }

    @GetMapping("/page")
    @RequirePermission(permissions = AuditKeys.PERMISSION_ONLINE_QUERY)
    public Object page(@RequestParam(required = false) String type,
                       @RequestParam(required = false) String tenantId,
                       @RequestParam(required = false) String deviceType,
                       @RequestParam(required = false) String loginAfter,
                       @RequestParam(required = false) String loginBefore,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(defaultValue = "LOGIN_TIME") String sortField,
                       @RequestParam(defaultValue = "true") boolean descending) {
        return onlineUserService.pageWithSummary(toQuery(type, tenantId, deviceType, loginAfter, loginBefore,
                keyword, page, size, sortField, descending));
    }

    @GetMapping("/stats")
    @RequirePermission(permissions = AuditKeys.PERMISSION_ONLINE_QUERY)
    public Object stats(@RequestParam(required = false) String type) {
        LoginType loginType = type == null || type.isBlank() ? null : LoginType.of(type);
        SessionStats snapshot = sessionStatsService.snapshot(null);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", loginType == null ? snapshot.total()
                : snapshot.byType().getOrDefault(loginType.value(), 0L));
        stats.put("byType", snapshot.byType());
        // 无租户会话的键为 null，Jackson 3 不接受 null 键，这里统一重命名
        Map<String, Long> byTenant = new LinkedHashMap<>();
        snapshot.byTenant().forEach((tenant, count) ->
                byTenant.merge(tenant == null ? "无租户" : tenant, count, Long::sum));
        stats.put("byTenant", byTenant);
        stats.put("byDeviceType", snapshot.byDeviceType());
        return stats;
    }

    @GetMapping("/export")
    @RequirePermission(permissions = AuditKeys.PERMISSION_ONLINE_EXPORT)
    public void export(@RequestParam(required = false) String type,
                       @RequestParam(required = false) String tenantId,
                       @RequestParam(required = false) String deviceType,
                       @RequestParam(required = false) String loginAfter,
                       @RequestParam(required = false) String loginBefore,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(defaultValue = "LOGIN_TIME") String sortField,
                       @RequestParam(defaultValue = "true") boolean descending,
                       HttpServletResponse response) {
        OnlineQuery query = toQuery(type, tenantId, deviceType, loginAfter, loginBefore, keyword, 1,
                OnlineQuery.MAX_SIZE, sortField, descending);
        List<SessionInfo> sessions = onlineUserService.list(query);
        excelService.export(DownloadResponses.attachmentStream(response, "online-users.xlsx",
                DownloadResponses.CONTENT_TYPE_EXCEL), sessions);
    }

    @PostMapping("/kick")
    @RequirePermission(permissions = AuditKeys.PERMISSION_ONLINE_KICK)
    public Object kick(@RequestBody KickRequest request, HttpServletRequest httpRequest) {
        LoginType type = request.type() == null || request.type().isBlank() ? LoginType.TENANT_USER
                : LoginType.of(request.type());
        if (type == null) {
            throw new IllegalArgumentException("未知的账号体系：" + request.type());
        }
        boolean kicked;
        if (request.token() != null && !request.token().isBlank()) {
            kicked = kickSessionService.kickoutByToken(type, request.token(), request.reason());
            recordAdminKick(httpRequest, type, request.token(), request.reason(), 1);
        } else if (request.loginId() != null && !request.loginId().isBlank()) {
            kickSessionService.kickout(type, request.loginId(), request.reason());
            kicked = true;
            recordAdminKick(httpRequest, type, request.loginId(), request.reason(), 1);
        } else {
            throw new IllegalArgumentException("token 与 loginId 至少提供一个");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kicked", kicked);
        return result;
    }

    @PostMapping("/kick/batch")
    @RequirePermission(permissions = AuditKeys.PERMISSION_ONLINE_KICK)
    public Object kickBatch(@RequestBody BatchKickRequest request, HttpServletRequest httpRequest) {
        LoginType type = request.type() == null || request.type().isBlank() ? LoginType.TENANT_USER
                : LoginType.of(request.type());
        if (type == null) {
            throw new IllegalArgumentException("未知的账号体系：" + request.type());
        }
        int count = kickSessionService.batchKickout(type, request.loginIds(), request.reason());
        recordAdminKick(httpRequest, type, "批量 " + count + " 个账号", request.reason(), count);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kicked", count);
        return result;
    }

    @PostMapping("/kick/tenant")
    @RequirePermission(permissions = AuditKeys.PERMISSION_ONLINE_KICK)
    public Object kickTenant(@RequestBody TenantKickRequest request, HttpServletRequest httpRequest) {
        LoginType type = request.type() == null || request.type().isBlank() ? LoginType.TENANT_USER
                : LoginType.of(request.type());
        if (type == null) {
            throw new IllegalArgumentException("未知的账号体系：" + request.type());
        }
        int count = kickSessionService.kickoutByTenant(type, request.tenantId(), request.reason());
        recordAdminKick(httpRequest, type, "租户 " + request.tenantId(), request.reason(), count);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kicked", count);
        return result;
    }

    private OnlineQuery toQuery(String type, String tenantId, String deviceType, String loginAfter,
                                String loginBefore, String keyword, int page, int size, String sortField,
                                boolean descending) {
        LoginType loginType = type == null || type.isBlank() ? null : LoginType.of(type);
        SessionSortField sort = sortField == null || sortField.isBlank() ? SessionSortField.LOGIN_TIME
                : SessionSortField.valueOf(sortField);
        return new OnlineQuery(loginType, blankToNull(tenantId), blankToNull(deviceType),
                parseTime(loginAfter), parseTime(loginBefore), blankToNull(keyword), page, size, sort, descending);
    }

    private void recordAdminKick(HttpServletRequest request, LoginType type, Object target, String reason, int count) {
        AuditLogRecord record = new AuditLogRecord();
        record.setAuditId(UUID.randomUUID().toString());
        record.setCategory(AuditCategory.SECURITY.name());
        record.setOperation(AuditOperation.OTHER.name());
        record.setLevel(AuditLevel.WARN.name());
        record.setModule("audit");
        record.setDescription("管理员发起强制下线（" + count + " 个会话）："
                + (reason == null || reason.isBlank() ? KickSessionService.DEFAULT_REASON : reason));
        record.setLoginType(type.value());
        record.setTarget(String.valueOf(target));
        resolveOperator(request, record);
        record.setClientIp(request.getRemoteAddr());
        record.setRequestPath(request.getRequestURI());
        record.setHttpMethod(request.getMethod());
        record.setCreateTime(System.currentTimeMillis());
        auditLogService.insert(record);
    }

    private void resolveOperator(HttpServletRequest request, AuditLogRecord record) {
        String headerValue = request.getHeader(LOGIN_TYPE_HEADER);
        LoginType type = headerValue == null ? LoginType.TENANT_USER : LoginType.of(headerValue);
        if (type == null) {
            return;
        }
        try {
            Object loginId = StpKit.of(type).getLoginIdDefaultNull();
            if (loginId != null) {
                record.setLoginId(String.valueOf(loginId));
                record.setUserId(Long.valueOf(String.valueOf(loginId)));
            }
        } catch (NumberFormatException | IllegalStateException e) {
            // 操作者解析失败不影响踢人审计本身
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private LocalDateTime parseTime(String value) {
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value);
    }

    public record KickRequest(String type, String token, String loginId, String reason) {
    }

    public record BatchKickRequest(String type, List<Long> loginIds, String reason) {
    }

    public record TenantKickRequest(String type, String tenantId, String reason) {
    }
}
