package cn.slainc.modules.auth;

import cn.slainc.core.enums.LoginType;
import cn.slainc.crypto.hash.BCryptUtils;
import cn.slainc.security.spi.AccountAdminPort;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.AccountPasswordUpdater;
import cn.slainc.security.spi.AccountRestrictionPort;
import cn.slainc.security.spi.PasswordAccount;
import cn.slainc.security.spi.SecurityPermissionProvider;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// 模块级测试的内存账号源：替掉真实 upms，提供账号查询、改密落库、注销软删与用户级登录限制裁决
public class TestAccountRegistry implements AccountAuthProvider, AccountPasswordUpdater, AccountAdminPort,
        AccountRestrictionPort, SecurityPermissionProvider {

    public static final String PASSWORD = "Password#123";

    private final Map<String, PasswordAccount> accounts = new LinkedHashMap<>();

    private final Map<Long, Boolean> deleted = new ConcurrentHashMap<>();

    private final Set<Long> deleteFailures = ConcurrentHashMap.newKeySet();

    private final Map<String, List<String>> restrictions = new ConcurrentHashMap<>();

    private final Map<Long, List<String>> permissionOverrides = new ConcurrentHashMap<>();

    private final AtomicLong sequence = new AtomicLong(9000);

    public synchronized PasswordAccount create(String username) {
        return create(username, PASSWORD);
    }

    public synchronized PasswordAccount create(String username, String password) {
        long userId = sequence.incrementAndGet();
        PasswordAccount account = new PasswordAccount(userId, username, BCryptUtils.hash(password), true,
                LocalDateTime.now().minusDays(1), false);
        accounts.put(key(username, LoginType.TENANT_USER), account);
        return account;
    }

    // 预置用户级登录限制：非空时该用户的下一次密码登录被拒
    public void restrict(String username, List<String> violations) {
        restrictions.put(username, List.copyOf(violations));
    }

    public void setPermissions(String username, List<String> permissions) {
        PasswordAccount account = findAccount(username, LoginType.TENANT_USER);
        if (account == null) {
            throw new IllegalArgumentException("测试账号不存在：" + username);
        }
        permissionOverrides.put(account.userId(), List.copyOf(permissions));
    }

    public boolean isDeleted(Long userId) {
        return Boolean.TRUE.equals(deleted.get(userId));
    }

    public void failDelete(Long userId, boolean fail) {
        if (fail) {
            deleteFailures.add(userId);
        } else {
            deleteFailures.remove(userId);
        }
    }

    @Override
    public PasswordAccount findAccount(String username, LoginType type) {
        PasswordAccount account = accounts.get(key(username, type));
        return account == null || Boolean.TRUE.equals(deleted.get(account.userId())) ? null : account;
    }

    @Override
    public PasswordAccount findAccountByUserId(Long userId, LoginType type) {
        return accounts.values().stream()
                .filter(account -> account.userId().equals(userId))
                .filter(account -> !Boolean.TRUE.equals(deleted.get(userId)))
                .findFirst().orElse(null);
    }

    @Override
    public void updatePassword(Long userId, String newPasswordBcrypt, LoginType type) {
        PasswordAccount current = findAccountByUserId(userId, type);
        if (current == null) {
            throw new IllegalStateException("用户不存在或已注销: " + userId + "，无法更新密码");
        }
        accounts.put(key(current.username(), type), new PasswordAccount(userId, current.username(),
                newPasswordBcrypt, true, LocalDateTime.now(), false));
    }

    @Override
    public Long tenantIdOf(Long userId) {
        return 1L;
    }

    @Override
    public void delete(Long userId) {
        if (deleteFailures.contains(userId)) {
            throw new IllegalStateException("模拟注销删除失败");
        }
        deleted.put(userId, true);
    }

    @Override
    public List<String> validate(String username, LoginType type, String clientIp, String deviceType) {
        return restrictions.getOrDefault(username, List.of());
    }

    @Override
    public List<String> getPermissions(Object loginId, LoginType type) {
        Long userId = loginId instanceof Number number ? number.longValue() : null;
        if (userId == null && loginId != null) {
            try {
                userId = Long.parseLong(loginId.toString());
            } catch (NumberFormatException ignored) {
                userId = null;
            }
        }
        if (userId != null && permissionOverrides.containsKey(userId)) {
            return permissionOverrides.get(userId);
        }
        return List.of(AuthKeys.PERMISSION_USER_QUERY, AuthKeys.PERMISSION_USER_UPDATE,
                AuthKeys.PERMISSION_MFA_POLICY_QUERY, AuthKeys.PERMISSION_MFA_POLICY_UPDATE,
                AuthKeys.PERMISSION_LOGIN_POLICY_QUERY, AuthKeys.PERMISSION_LOGIN_POLICY_UPDATE,
                AuthKeys.PERMISSION_SESSION_POLICY_QUERY, AuthKeys.PERMISSION_SESSION_POLICY_UPDATE,
                AuthKeys.PERMISSION_THIRD_CONFIG_QUERY, AuthKeys.PERMISSION_THIRD_CONFIG_UPDATE,
                AuthKeys.PERMISSION_THIRD_SYNC_EXECUTE, AuthKeys.PERMISSION_THIRD_SYNC_QUERY,
                AuthKeys.PERMISSION_THIRD_SYNC_EXPORT,
                AuthKeys.PERMISSION_OAUTH2_CLIENT_QUERY, AuthKeys.PERMISSION_OAUTH2_CLIENT_UPDATE,
                AuthKeys.PERMISSION_SSO_CLIENT_QUERY, AuthKeys.PERMISSION_SSO_CLIENT_UPDATE);
    }

    @Override
    public List<String> getRoles(Object loginId, LoginType type) {
        return List.of("test-admin");
    }

    private String key(String username, LoginType type) {
        return type.value() + ":" + username;
    }
}
