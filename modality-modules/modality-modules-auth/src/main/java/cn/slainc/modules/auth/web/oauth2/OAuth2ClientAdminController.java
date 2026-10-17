package cn.slainc.modules.auth.web.oauth2;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.Result;
import cn.slainc.core.result.ResultCode;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.auth.AuthKeys;
import cn.slainc.oauth2.client.ClientAdminService;
import cn.slainc.oauth2.client.Oauth2ClientRecord;
import cn.slainc.oauth2.config.Oauth2Properties;
import cn.slainc.oauth2.scope.ScopeDefinition;
import cn.slainc.oauth2.scope.ScopeRegistry;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Tag(name = "OAuth2 客户端管理", description = "OAuth2/OIDC 系统接入客户端、授权范围与密钥管理")
@RestController
@RequestMapping("/auth/oauth2/clients")
public class OAuth2ClientAdminController {

    private final ClientAdminService clientAdminService;

    private final ScopeRegistry scopeRegistry;

    private final Oauth2Properties properties;

    public OAuth2ClientAdminController(ClientAdminService clientAdminService, ScopeRegistry scopeRegistry,
                                       Oauth2Properties properties) {
        this.clientAdminService = clientAdminService;
        this.scopeRegistry = scopeRegistry;
        this.properties = properties;
    }

    public record OAuth2ClientRequest(String clientId, String clientName, String clientSecret,
                                      List<String> scopes, List<String> redirectUris,
                                      List<String> grantTypes, String subjectId,
                                      Long accessTokenTimeout, Long refreshTokenTimeout,
                                      Long clientTokenTimeout, Integer maxAccessTokenCount,
                                      Integer maxRefreshTokenCount, Integer maxClientTokenCount,
                                      Boolean newRefresh, Boolean autoConfirm,
                                      List<String> ipWhitelist, Long qpsLimit,
                                      String status, String remark) {
    }

    public record OAuth2ClientView(String clientId, String clientName, List<String> scopes,
                                   List<String> redirectUris, List<String> grantTypes,
                                   String subjectId, long accessTokenTimeout,
                                   long refreshTokenTimeout, long clientTokenTimeout,
                                   int maxAccessTokenCount, int maxRefreshTokenCount,
                                   int maxClientTokenCount, boolean newRefresh,
                                   boolean autoConfirm, List<String> ipWhitelist,
                                   long qpsLimit, String status, String remark,
                                   boolean secretConfigured, Long previousSecretExpireAt,
                                   Long createdAt, Long updatedAt) {

        static OAuth2ClientView of(Oauth2ClientRecord record) {
            return new OAuth2ClientView(record.getClientId(), record.getClientName(),
                    List.copyOf(record.getContractScopes()), List.copyOf(record.getAllowRedirectUris()),
                    List.copyOf(record.getAllowGrantTypes()), record.getSubjectId(),
                    record.getAccessTokenTimeout(), record.getRefreshTokenTimeout(),
                    record.getClientTokenTimeout(), record.getMaxAccessTokenCount(),
                    record.getMaxRefreshTokenCount(), record.getMaxClientTokenCount(),
                    record.isNewRefresh(), record.isAutoConfirm(),
                    List.copyOf(record.getIpWhitelist()), record.getQpsLimit(), record.getStatus(),
                    record.getRemark(), record.getClientSecret() != null && !record.getClientSecret().isBlank(),
                    record.getPreviousSecretExpireAt(), record.getCreatedAt(), record.getUpdatedAt());
        }
    }

    public record OAuth2ClientSecretView(OAuth2ClientView client, String clientSecret) {
    }

    public record OAuth2ScopeView(String name, String description, String group, String riskLevel) {
    }

    public record ClientStatusRequest(Boolean enabled) {
    }

    public record SecretRotateRequest(Long graceSeconds) {
    }

    @GetMapping
    @RequirePermission(permissions = AuthKeys.PERMISSION_OAUTH2_CLIENT_QUERY)
    public Result<List<OAuth2ClientView>> list() {
        return Result.ok(clientAdminService.list().stream()
                .sorted(Comparator.comparing(Oauth2ClientRecord::getClientId))
                .map(OAuth2ClientView::of)
                .toList());
    }

    @GetMapping("/{clientId}")
    @RequirePermission(permissions = AuthKeys.PERMISSION_OAUTH2_CLIENT_QUERY)
    public Result<OAuth2ClientView> detail(@PathVariable String clientId) {
        return Result.ok(OAuth2ClientView.of(badRequest(() -> clientAdminService.find(clientId))));
    }

    @GetMapping("/scopes")
    @RequirePermission(permissions = AuthKeys.PERMISSION_OAUTH2_CLIENT_QUERY)
    public Result<List<OAuth2ScopeView>> scopes() {
        return Result.ok(scopeRegistry.list().stream()
                .sorted(Comparator.comparing(ScopeDefinition::getName))
                .map(scope -> new OAuth2ScopeView(scope.getName(), scope.getDescription(), scope.getGroup(),
                        scope.getRiskLevel().name()))
                .toList());
    }

    @PostMapping
    @RequirePermission(permissions = AuthKeys.PERMISSION_OAUTH2_CLIENT_UPDATE)
    @AuditLog(value = "创建 OAuth2 客户端", operation = AuditOperation.CREATE,
            category = AuditCategory.SECURITY, module = "auth")
    public Result<OAuth2ClientSecretView> create(@RequestBody OAuth2ClientRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "OAuth2 客户端配置不能为空");
        }
        String plainSecret = text(request.clientSecret());
        if (plainSecret == null) {
            plainSecret = ClientAdminService.randomSecret();
        }
        Oauth2ClientRecord record = new Oauth2ClientRecord();
        record.setClientId(text(request.clientId()));
        record.setClientName(defaultText(request.clientName(), text(request.clientId())));
        record.setClientSecret(plainSecret);
        record.setContractScopes(normalized(request.scopes()));
        record.setAllowRedirectUris(normalized(request.redirectUris()));
        List<String> grantTypes = normalized(request.grantTypes());
        record.setAllowGrantTypes(grantTypes.isEmpty()
                ? List.of("authorization_code", "refresh_token") : grantTypes);
        record.setSubjectId(defaultText(request.subjectId(), ""));
        record.setAccessTokenTimeout(value(request.accessTokenTimeout(),
                properties.getAccessTokenTimeoutSeconds()));
        record.setRefreshTokenTimeout(value(request.refreshTokenTimeout(),
                properties.getRefreshTokenTimeoutSeconds()));
        record.setClientTokenTimeout(value(request.clientTokenTimeout(),
                properties.getClientTokenTimeoutSeconds()));
        record.setMaxAccessTokenCount(value(request.maxAccessTokenCount(), 0));
        record.setMaxRefreshTokenCount(value(request.maxRefreshTokenCount(), 0));
        record.setMaxClientTokenCount(value(request.maxClientTokenCount(), 0));
        record.setNewRefresh(request.newRefresh() == null ? properties.isNewRefresh() : request.newRefresh());
        record.setAutoConfirm(Boolean.TRUE.equals(request.autoConfirm()));
        record.setIpWhitelist(normalized(request.ipWhitelist()));
        record.setQpsLimit(request.qpsLimit() == null
                ? properties.getGuard().getDefaultQpsLimit() : Math.max(0, request.qpsLimit()));
        record.setRemark(defaultText(request.remark(), ""));
        Oauth2ClientRecord created = badRequest(() -> clientAdminService.register(record));
        return Result.ok(new OAuth2ClientSecretView(OAuth2ClientView.of(created), plainSecret));
    }

    @PutMapping("/{clientId}")
    @RequirePermission(permissions = AuthKeys.PERMISSION_OAUTH2_CLIENT_UPDATE)
    @AuditLog(value = "修改 OAuth2 客户端", operation = AuditOperation.UPDATE,
            category = AuditCategory.SECURITY, module = "auth")
    public Result<OAuth2ClientView> update(@PathVariable String clientId,
                                            @RequestBody OAuth2ClientRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "OAuth2 客户端配置不能为空");
        }
        Oauth2ClientRecord existing = badRequest(() -> clientAdminService.find(clientId));
        validateStatus(request.status());
        Oauth2ClientRecord input = new Oauth2ClientRecord();
        input.setClientId(clientId);
        input.setClientSecret(text(request.clientSecret()));
        input.setClientName(request.clientName());
        input.setContractScopes(request.scopes() == null ? null : normalized(request.scopes()));
        input.setAllowRedirectUris(request.redirectUris() == null ? null : normalized(request.redirectUris()));
        input.setAllowGrantTypes(request.grantTypes() == null ? null : normalized(request.grantTypes()));
        input.setSubjectId(request.subjectId());
        input.setAccessTokenTimeout(request.accessTokenTimeout() == null
                ? 0 : Math.max(0, request.accessTokenTimeout()));
        input.setRefreshTokenTimeout(request.refreshTokenTimeout() == null
                ? 0 : Math.max(0, request.refreshTokenTimeout()));
        input.setClientTokenTimeout(request.clientTokenTimeout() == null
                ? 0 : Math.max(0, request.clientTokenTimeout()));
        input.setMaxAccessTokenCount(request.maxAccessTokenCount() == null
                ? -1 : Math.max(0, request.maxAccessTokenCount()));
        input.setMaxRefreshTokenCount(request.maxRefreshTokenCount() == null
                ? -1 : Math.max(0, request.maxRefreshTokenCount()));
        input.setMaxClientTokenCount(request.maxClientTokenCount() == null
                ? -1 : Math.max(0, request.maxClientTokenCount()));
        input.setNewRefresh(request.newRefresh() == null ? existing.isNewRefresh() : request.newRefresh());
        input.setAutoConfirm(request.autoConfirm() == null ? existing.isAutoConfirm() : request.autoConfirm());
        input.setIpWhitelist(request.ipWhitelist() == null ? null : normalized(request.ipWhitelist()));
        input.setQpsLimit(request.qpsLimit() == null ? -1 : Math.max(0, request.qpsLimit()));
        input.setStatus(text(request.status()));
        input.setRemark(request.remark());
        Oauth2ClientRecord updated = badRequest(() -> clientAdminService.update(input));
        return Result.ok(OAuth2ClientView.of(updated));
    }

    @PostMapping("/{clientId}/status")
    @RequirePermission(permissions = AuthKeys.PERMISSION_OAUTH2_CLIENT_UPDATE)
    @AuditLog(value = "修改 OAuth2 客户端状态", operation = AuditOperation.UPDATE,
            category = AuditCategory.SECURITY, module = "auth")
    public Result<OAuth2ClientView> changeStatus(@PathVariable String clientId,
                                                  @RequestBody ClientStatusRequest request) {
        if (request == null || request.enabled() == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "客户端状态不能为空");
        }
        badRequest(() -> {
            clientAdminService.changeStatus(clientId, request.enabled());
            return null;
        });
        return Result.ok(OAuth2ClientView.of(badRequest(() -> clientAdminService.find(clientId))));
    }

    @PostMapping("/{clientId}/secret/rotate")
    @RequirePermission(permissions = AuthKeys.PERMISSION_OAUTH2_CLIENT_UPDATE)
    @AuditLog(value = "轮换 OAuth2 客户端密钥", operation = AuditOperation.UPDATE,
            category = AuditCategory.SECURITY, module = "auth")
    public Result<OAuth2ClientSecretView> rotateSecret(@PathVariable String clientId,
                                                        @RequestBody(required = false) SecretRotateRequest request) {
        long graceSeconds = request == null || request.graceSeconds() == null
                ? 0 : Math.max(0, Math.min(request.graceSeconds(), 86_400));
        Oauth2ClientRecord record = badRequest(() ->
                clientAdminService.rotateSecret(clientId, graceSeconds * 1000));
        return Result.ok(new OAuth2ClientSecretView(OAuth2ClientView.of(record), record.getClientSecret()));
    }

    @DeleteMapping("/{clientId}")
    @RequirePermission(permissions = AuthKeys.PERMISSION_OAUTH2_CLIENT_UPDATE)
    @AuditLog(value = "删除 OAuth2 客户端", operation = AuditOperation.DELETE,
            category = AuditCategory.SECURITY, module = "auth")
    public Result<Void> delete(@PathVariable String clientId) {
        badRequest(() -> {
            clientAdminService.remove(clientId);
            return null;
        });
        return Result.ok();
    }

    private List<String> normalized(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            for (String part : value.split("[,\\n，]")) {
                if (!part.isBlank()) {
                    result.add(part.trim());
                }
            }
        }
        return new ArrayList<>(result);
    }

    private void validateStatus(String status) {
        if (text(status) != null
                && !Oauth2ClientRecord.STATUS_ENABLED.equals(status)
                && !Oauth2ClientRecord.STATUS_DISABLED.equals(status)) {
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

    private long value(Long value, long fallback) {
        return value == null ? fallback : Math.max(0, value);
    }

    private int value(Integer value, int fallback) {
        return value == null ? fallback : Math.max(0, value);
    }

    private <T> T badRequest(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new BizException(ResultCode.BAD_REQUEST, exception.getMessage());
        }
    }
}
