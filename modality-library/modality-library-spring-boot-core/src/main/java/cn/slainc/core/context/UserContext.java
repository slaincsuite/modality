package cn.slainc.core.context;

import cn.slainc.core.enums.LoginType;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class UserContext {

    private static final ThreadLocal<UserInfo> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(UserInfo userInfo) {
        if (userInfo == null) {
            HOLDER.remove();
        } else {
            HOLDER.set(userInfo);
        }
    }

    public static UserInfo get() {
        return HOLDER.get();
    }

    public static boolean isAuthenticated() {
        return HOLDER.get() != null;
    }

    public static Long userId() {
        UserInfo info = HOLDER.get();
        return info == null ? null : info.userId();
    }

    public static String username() {
        UserInfo info = HOLDER.get();
        return info == null ? null : info.username();
    }

    public static LoginType loginType() {
        UserInfo info = HOLDER.get();
        return info == null ? null : info.loginType();
    }

    public static Set<String> roles() {
        UserInfo info = HOLDER.get();
        return info == null ? Set.of() : info.roles();
    }

    public static Set<String> permissions() {
        UserInfo info = HOLDER.get();
        return info == null ? Set.of() : info.permissions();
    }

    public static boolean hasRole(String role) {
        return role != null && roles().contains(role);
    }

    public static boolean hasPermission(String permission) {
        return permission != null && permissions().contains(permission);
    }

    public static boolean isSuperAdmin() {
        UserInfo info = HOLDER.get();
        return info != null && info.superAdmin();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public record UserInfo(Long userId, String username, LoginType loginType,
                           Set<String> roles, Set<String> permissions, boolean superAdmin) {

        public UserInfo {
            roles = roles == null ? Collections.emptySet() : new LinkedHashSet<>(roles);
            permissions = permissions == null ? Collections.emptySet() : new LinkedHashSet<>(permissions);
        }

        public static UserInfo of(Long userId, String username, LoginType loginType) {
            return new UserInfo(userId, username, loginType, Set.of(), Set.of(), false);
        }
    }
}
