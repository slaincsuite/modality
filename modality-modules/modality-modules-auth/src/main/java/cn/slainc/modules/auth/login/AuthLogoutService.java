package cn.slainc.modules.auth.login;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.event.LogoutEvent;
import cn.slainc.core.exception.BizException;
import cn.slainc.security.StpKit;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.PasswordAccount;
import cn.slainc.utils.json.JsonUtils;
import cn.slainc.web.websocket.WebSocketBroadcaster;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

public class AuthLogoutService {

    private final ApplicationEventPublisher publisher;

    private final ObjectProvider<AccountAuthProvider> authProvider;

    private final ObjectProvider<WebSocketBroadcaster> broadcaster;

    public AuthLogoutService(ApplicationEventPublisher publisher, ObjectProvider<AccountAuthProvider> authProvider,
                             ObjectProvider<WebSocketBroadcaster> broadcaster) {
        this.publisher = publisher;
        this.authProvider = authProvider;
        this.broadcaster = broadcaster;
    }

    public boolean logoutCurrent(LoginType type) {
        if (!StpKit.of(type).isLogin()) {
            return false;
        }
        Object loginId = StpKit.of(type).getLoginId();
        String tokenValue = StpKit.of(type).getTokenValue();
        PasswordAccount account = findAccount(type, loginId);
        StpKit.of(type).logoutByTokenValue(tokenValue);
        publishLogout(type, loginId, account, false);
        return true;
    }

    public int logoutAll(LoginType type) {
        if (!StpKit.of(type).isLogin()) {
            return 0;
        }
        Object loginId = StpKit.of(type).getLoginId();
        PasswordAccount account = findAccount(type, loginId);
        int terminals = StpKit.of(type).getTerminalListByLoginId(loginId) == null
                ? 0 : StpKit.of(type).getTerminalListByLoginId(loginId).size();
        StpKit.of(type).logout(loginId);
        publishLogout(type, loginId, account, false);
        return terminals;
    }

    private PasswordAccount findAccount(LoginType type, Object loginId) {
        AccountAuthProvider provider = authProvider.getIfAvailable();
        Long userId = CurrentLogin.userId(loginId);
        if (provider == null || userId == null) {
            return null;
        }
        try {
            return provider.findAccountByUserId(userId, type);
        } catch (Exception e) {
            return null;
        }
    }

    private void publishLogout(LoginType type, Object loginId, PasswordAccount account, boolean forced) {
        Long userId = CurrentLogin.userId(loginId);
        publisher.publishEvent(new LogoutEvent(userId, account == null ? null : account.username(), type, forced));
        WebSocketBroadcaster socket = broadcaster.getIfAvailable();
        if (socket != null) {
            socket.sendToUser(String.valueOf(loginId),
                    JsonUtils.toJson(Map.of("type", "logout", "loginType", type.value(), "at",
                            System.currentTimeMillis())));
        }
    }

    public String username(LoginType type, Long userId) {
        PasswordAccount account = userId == null ? null : findAccount(type, userId);
        return account == null ? null : account.username();
    }

    public void checkPassword(LoginType type, Long userId, String rawPassword) {
        AccountAuthProvider provider = authProvider.getIfAvailable();
        if (provider == null) {
            throw new BizException("未接入账号数据源，无法校验密码");
        }
        PasswordAccount account = provider.findAccountByUserId(userId, type);
        if (account == null || !account.enabled()) {
            throw new BizException("账号不存在或已被禁用");
        }
        if (rawPassword == null || !cn.slainc.crypto.hash.BCryptUtils.verifyQuietly(rawPassword,
                account.passwordHash())) {
            throw new BizException("密码错误");
        }
    }
}
