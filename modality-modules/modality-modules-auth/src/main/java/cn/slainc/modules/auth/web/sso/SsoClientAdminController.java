package cn.slainc.modules.auth.web.sso;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.Result;
import cn.slainc.core.result.ResultCode;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.auth.AuthKeys;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.sso.client.SsoClientAdminService;
import cn.slainc.sso.client.SsoClientRecord;
import cn.slainc.sso.config.SsoProperties;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Tag(name = "SSO 客户端管理", description = "单点登录系统接入客户端与密钥管理")
@RestController
@RequestMapping("/auth/sso/clients")
public class SsoClientAdminController {

    private static final String DEFAULT_PUSH_URL = "/sso/pushC";

    private final SsoClientAdminService clientAdminService;

    private final SsoProperties properties;

    public SsoClientAdminController(SsoClientAdminService clientAdminService, SsoProperties properties) {
        this.clientAdminService = clientAdminService;
        this.properties = properties;
    }

    public record SsoClientRequest(String client, String clientName, String secretKey,
                                   String allowUrl, String baseUrl, String pushUrl,
                                   Boolean push, Boolean slo, String status) {
    }

    public record SsoClientView(String client, String clientName, String allowUrl,
                                String baseUrl, String pushUrl, boolean push, boolean slo,
                                String status, boolean secretConfigured,
                                Long createdAt, Long updatedAt) {

        static SsoClientView of(SsoClientRecord record) {
            return new SsoClientView(record.getClient(), record.getClientName(), record.getAllowUrl(),
                    record.getBaseUrl(), record.getPushUrl(), record.isPush(), record.isSlo(),
                    record.getStatus(), record.getSecretKey() != null && !record.getSecretKey().isBlank(),
                    record.getCreatedAt(), record.getUpdatedAt());
        }
    }

    public record SsoClientSecretView(SsoClientView client, String secretKey) {
    }

    public record SsoServerStatusView(boolean ssoEnabled, boolean serverConfigured,
                                      boolean clientModeEnabled, String loginType,
                                      long ticketTimeoutSeconds, String clientId) {
    }

    public record ClientStatusRequest(Boolean enabled) {
    }

    @GetMapping
    @RequirePermission(permissions = AuthKeys.PERMISSION_SSO_CLIENT_QUERY)
    public Result<List<SsoClientView>> list() {
        return Result.ok(clientAdminService.list().stream()
                .sorted(Comparator.comparing(SsoClientRecord::getClient))
                .map(SsoClientView::of)
                .toList());
    }

    @GetMapping("/{client}")
    @RequirePermission(permissions = AuthKeys.PERMISSION_SSO_CLIENT_QUERY)
    public Result<SsoClientView> detail(@PathVariable String client) {
        return Result.ok(SsoClientView.of(badRequest(() -> clientAdminService.find(client))));
    }

    @GetMapping("/server-status")
    @RequirePermission(permissions = AuthKeys.PERMISSION_SSO_CLIENT_QUERY)
    public Result<SsoServerStatusView> serverStatus() {
        boolean serverConfigured = properties.getServer().getSecretKey() != null
                && !properties.getServer().getSecretKey().isBlank();
        return Result.ok(new SsoServerStatusView(properties.isEnabled(), serverConfigured,
                properties.getClient().isEnabled(), properties.getLoginType(),
                properties.getServer().getTicketTimeoutSeconds(), properties.getClient().getClient()));
    }

    @PostMapping
    @RequirePermission(permissions = AuthKeys.PERMISSION_SSO_CLIENT_UPDATE)
    @AuditLog(value = "创建 SSO 客户端", operation = AuditOperation.CREATE,
            category = AuditCategory.SECURITY, module = "auth")
    public Result<SsoClientSecretView> create(@RequestBody SsoClientRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "SSO 客户端配置不能为空");
        }
        String plainSecret = text(request.secretKey());
        if (plainSecret == null) {
            plainSecret = SsoClientAdminService.randomSecret();
        }
        SsoClientRecord record = new SsoClientRecord();
        record.setClient(text(request.client()));
        record.setClientName(defaultText(request.clientName(), text(request.client())));
        record.setSecretKey(plainSecret);
        record.setAllowUrl(normalizedUrls(request.allowUrl()));
        record.setBaseUrl(defaultText(request.baseUrl(), ""));
        record.setPushUrl(defaultText(request.pushUrl(), DEFAULT_PUSH_URL));
        record.setPush(request.push() == null || request.push());
        record.setSlo(request.slo() == null || request.slo());
        SsoClientRecord created = badRequest(() -> clientAdminService.register(record));
        return Result.ok(new SsoClientSecretView(SsoClientView.of(created), plainSecret));
    }

    @PutMapping("/{client}")
    @RequirePermission(permissions = AuthKeys.PERMISSION_SSO_CLIENT_UPDATE)
    @AuditLog(value = "修改 SSO 客户端", operation = AuditOperation.UPDATE,
            category = AuditCategory.SECURITY, module = "auth")
    public Result<SsoClientView> update(@PathVariable String client,
                                        @RequestBody SsoClientRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "SSO 客户端配置不能为空");
        }
        validateStatus(request.status());
        SsoClientRecord existing = badRequest(() -> clientAdminService.find(client));
        SsoClientRecord input = new SsoClientRecord();
        input.setClient(client);
        input.setClientName(request.clientName());
        input.setSecretKey(text(request.secretKey()));
        input.setAllowUrl(request.allowUrl() == null ? null : normalizedUrls(request.allowUrl()));
        input.setBaseUrl(request.baseUrl());
        input.setPushUrl(request.pushUrl());
        input.setPush(request.push() == null ? existing.isPush() : request.push());
        input.setSlo(request.slo() == null ? existing.isSlo() : request.slo());
        input.setStatus(text(request.status()));
        SsoClientRecord updated = badRequest(() -> clientAdminService.update(input));
        return Result.ok(SsoClientView.of(updated));
    }

    @PostMapping("/{client}/status")
    @RequirePermission(permissions = AuthKeys.PERMISSION_SSO_CLIENT_UPDATE)
    @AuditLog(value = "修改 SSO 客户端状态", operation = AuditOperation.UPDATE,
            category = AuditCategory.SECURITY, module = "auth")
    public Result<SsoClientView> changeStatus(@PathVariable String client,
                                              @RequestBody ClientStatusRequest request) {
        if (request == null || request.enabled() == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "客户端状态不能为空");
        }
        badRequest(() -> {
            clientAdminService.changeStatus(client, request.enabled());
            return null;
        });
        return Result.ok(SsoClientView.of(badRequest(() -> clientAdminService.find(client))));
    }

    @PostMapping("/{client}/secret/rotate")
    @RequirePermission(permissions = AuthKeys.PERMISSION_SSO_CLIENT_UPDATE)
    @AuditLog(value = "轮换 SSO 客户端密钥", operation = AuditOperation.UPDATE,
            category = AuditCategory.SECURITY, module = "auth")
    public Result<SsoClientSecretView> rotateSecret(@PathVariable String client) {
        SsoClientRecord existing = badRequest(() -> clientAdminService.find(client));
        String plainSecret = SsoClientAdminService.randomSecret();
        SsoClientRecord input = new SsoClientRecord();
        input.setClient(client);
        input.setSecretKey(plainSecret);
        input.setClientName(existing.getClientName());
        input.setAllowUrl(existing.getAllowUrl());
        input.setBaseUrl(existing.getBaseUrl());
        input.setPushUrl(existing.getPushUrl());
        input.setPush(existing.isPush());
        input.setSlo(existing.isSlo());
        input.setStatus(existing.getStatus());
        SsoClientRecord updated = badRequest(() -> clientAdminService.update(input));
        return Result.ok(new SsoClientSecretView(SsoClientView.of(updated), plainSecret));
    }

    @DeleteMapping("/{client}")
    @RequirePermission(permissions = AuthKeys.PERMISSION_SSO_CLIENT_UPDATE)
    @AuditLog(value = "删除 SSO 客户端", operation = AuditOperation.DELETE,
            category = AuditCategory.SECURITY, module = "auth")
    public Result<Void> delete(@PathVariable String client) {
        badRequest(() -> {
            clientAdminService.remove(client);
            return null;
        });
        return Result.ok();
    }

    private String normalizedUrls(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        List<String> parts = Arrays.stream(value.split("[,\\n，]"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
        return String.join("\n", parts);
    }

    private void validateStatus(String status) {
        if (text(status) != null
                && !SsoClientRecord.STATUS_ENABLED.equals(status)
                && !SsoClientRecord.STATUS_DISABLED.equals(status)) {
            throw new BizException(ResultCode.BAD_REQUEST, "客户端状态只允许 enabled 或 disabled");
        }
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String defaultText(String value, String fallback) {
        String normalized = text(value);
        return normalized == null ? fallback : normalized;
    }

    private <T> T badRequest(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new BizException(ResultCode.BAD_REQUEST, exception.getMessage());
        }
    }
}
