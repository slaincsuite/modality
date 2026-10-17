package cn.slainc.modules.audit;

import cn.dev33.satoken.stp.StpInterface;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.result.Result;
import cn.slainc.crypto.hash.BCryptUtils;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.policy.aspect.RequirePolicy;
import cn.slainc.security.StpKit;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.PasswordAccount;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SpringBootConfiguration
@EnableAutoConfiguration
public class AuditModuleApplication {

    public static final String TEST_PASSWORD = "Password#123";

    @RestController
    public static class AuditTestController {

        @GetMapping("/audit-test/login")
        public Result<String> login() {
            StpKit.tenantUser().login(91001L);
            return Result.ok(StpKit.tenantUser().getTokenValue());
        }

        @GetMapping("/audit-test/login/{userId}")
        public Result<String> login(@org.springframework.web.bind.annotation.PathVariable Long userId) {
            StpKit.tenantUser().login(userId);
            return Result.ok(StpKit.tenantUser().getTokenValue());
        }

        @GetMapping("/audit-test/annotated")
        @AuditLog(value = "演示操作", operation = AuditOperation.EXPORT, category = AuditCategory.DATA,
                recordResult = true)
        public Result<String> annotated() {
            return Result.ok("annotated-ok");
        }

        @RequirePolicy(action = "modality:audit-test:write", resource = "modality:audit-test/data")
        @GetMapping("/audit-test/policy")
        public Result<String> policy() {
            return Result.ok("policy-ok");
        }
    }

    @Bean
    public AccountAuthProvider auditTestAccountProvider() {
        String hash = BCryptUtils.hash(TEST_PASSWORD);
        LocalDateTime changedAt = LocalDateTime.now().minusDays(1);
        Map<String, PasswordAccount> accounts = new LinkedHashMap<>();
        accounts.put(LoginType.TENANT_USER.value() + ":auditor",
                new PasswordAccount(91001L, "auditor", hash, true, changedAt, false));
        return new AccountAuthProvider() {

            @Override
            public PasswordAccount findAccount(String username, LoginType type) {
                return accounts.get(type.value() + ":" + username);
            }

            @Override
            public PasswordAccount findAccountByUserId(Long userId, LoginType type) {
                return accounts.values().stream()
                        .filter(account -> account.userId().equals(userId))
                        .findFirst().orElse(null);
            }
        };
    }

    // 无 Redis 环境没有 SecurityPermissionCache，直接以 sa-token 的 StpInterface 提供测试权限
    @Bean
    public StpInterface auditTestPermissionProvider() {
        return new StpInterface() {

            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                return "91002".equals(String.valueOf(loginId)) ? List.of() : List.of("*");
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                return List.of();
            }
        };
    }
}
