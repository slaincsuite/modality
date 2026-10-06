package cn.slainc.modules.auth.service;
import cn.slainc.modules.auth.context.CurrentLogin;
import cn.slainc.modules.auth.third.ThirdBindTicket;
import cn.slainc.modules.auth.store.AuthTicketStore;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.crypto.hash.BCryptUtils;
import cn.slainc.modules.auth.config.AuthProperties;
import cn.slainc.modules.auth.service.AuthSessionService;
import cn.slainc.security.LoginTypes;
import cn.slainc.security.lock.AccountLockService;
import cn.slainc.security.login.LoginResult;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.PasswordAccount;
import cn.slainc.sensitive.xss.XssSanitizer;
import cn.slainc.thirdauth.ThirdAuthService;
import cn.slainc.thirdauth.ThirdAuthService.ThirdAuthLoginContext;
import cn.slainc.thirdauth.ThirdAuthService.ThirdAuthLoginResult;
import cn.slainc.thirdauth.ThirdPartyPlatform;
import cn.slainc.thirdauth.ThirdPartyUser;
import cn.slainc.thirdauth.binding.ThirdAccountBinding;
import org.springframework.beans.factory.ObjectProvider;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

public class ThirdPartyLoginService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ThirdAuthService thirdAuthService;

    private final AuthTicketStore<ThirdBindTicket> bindStore;

    private final AuthProperties properties;

    private final AuthSessionService sessionService;

    private final ObjectProvider<AccountAuthProvider> authProvider;

    private final ObjectProvider<AccountLockService> lockService;

    private final ObjectProvider<XssSanitizer> sanitizer;

    public ThirdPartyLoginService(ThirdAuthService thirdAuthService, AuthTicketStore<ThirdBindTicket> bindStore,
                                  AuthProperties properties, AuthSessionService sessionService,
                                  ObjectProvider<AccountAuthProvider> authProvider,
                                  ObjectProvider<AccountLockService> lockService,
                                  ObjectProvider<XssSanitizer> sanitizer) {
        this.thirdAuthService = thirdAuthService;
        this.bindStore = bindStore;
        this.properties = properties;
        this.sessionService = sessionService;
        this.authProvider = authProvider;
        this.lockService = lockService;
        this.sanitizer = sanitizer;
    }

    public record ThirdProfile(String platform, String openId, String unionId, String nickname, String avatar) {

        public static ThirdProfile of(ThirdPartyUser user) {
            return new ThirdProfile(user.getPlatform() == null ? null : user.getPlatform().code(), user.getOpenId(),
                    user.getUnionId(), user.getNickname(), user.getAvatar());
        }
    }

    public record ThirdLoginView(boolean needBind, String bindTicketId, LoginResult login, ThirdProfile profile) {
    }

    public ThirdAuthLoginContext authorizeUrl(String platform) {
        return thirdAuthService.beginLogin(resolve(platform));
    }

    public ThirdLoginView callback(String platform, Map<String, String> params) {
        ThirdAuthLoginResult result = thirdAuthService.handleCallback(platform, params);
        ThirdProfile profile = ThirdProfile.of(result.user());
        if (result.bound()) {
            LoginType type = loginType();
            LoginResult login = sessionService.establish(type, Long.parseLong(result.userId()), "third", null);
            return new ThirdLoginView(false, null, login, profile);
        }
        ThirdBindTicket ticket = issueBindTicket(result.user());
        return new ThirdLoginView(true, ticket.getTicketId(), null, profile);
    }

    public LoginResult bindWithPassword(String bindTicketId, LoginType type, String username, String password,
                                        String deviceType, String deviceId) {
        ThirdBindTicket ticket = consume(bindTicketId);
        AccountAuthProvider provider = authProvider.getIfAvailable();
        if (provider == null) {
            throw new BizException("未接入账号数据源，无法完成绑定");
        }
        PasswordAccount account = provider.findAccount(username, type);
        if (account == null || !BCryptUtils.verifyQuietly(password, account.passwordHash())) {
            AccountLockService lock = lockService.getIfAvailable();
            if (lock != null && account != null) {
                lock.recordFailure(type, account.userId());
            }
            throw new BizException("账号不存在或密码错误，绑定失败");
        }
        if (!account.enabled()) {
            throw new BizException("账号已被禁用，绑定失败");
        }
        thirdAuthService.bind(toUser(ticket), String.valueOf(account.userId()));
        return sessionService.establish(type, account.userId(), deviceType, deviceId);
    }

    public void bindCurrent(String bindTicketId, LoginType type, Object loginId) {
        ThirdBindTicket ticket = consume(bindTicketId);
        Long userId = cn.slainc.modules.auth.context.CurrentLogin.userId(loginId);
        if (userId == null) {
            throw new BizException("当前登录身份缺少用户 ID，无法绑定");
        }
        thirdAuthService.bind(toUser(ticket), String.valueOf(userId));
    }

    public List<ThirdAccountBinding> bindings(Object userId) {
        return thirdAuthService.listBindings(String.valueOf(userId));
    }

    public void unbind(String platform, String openId, Object userId) {
        thirdAuthService.unbind(resolve(platform), openId, String.valueOf(userId));
    }

    private ThirdBindTicket issueBindTicket(ThirdPartyUser user) {
        ThirdBindTicket ticket = new ThirdBindTicket();
        ticket.setTicketId(randomTicketId());
        ticket.setPlatform(user.getPlatform() == null ? null : user.getPlatform().code());
        ticket.setOpenId(user.getOpenId());
        ticket.setUnionId(user.getUnionId());
        ticket.setNickname(clean(user.getNickname()));
        ticket.setAvatar(user.getAvatar());
        long ttl = properties.getThird().getBindTicketTtlSeconds();
        ticket.setCreatedAt(System.currentTimeMillis());
        ticket.setExpireAt(ticket.getCreatedAt() + ttl * 1000);
        bindStore.save(ticket, ttl);
        return ticket;
    }

    private ThirdBindTicket consume(String ticketId) {
        return bindStore.consume(ticketId)
                .orElseThrow(() -> new BizException("绑定凭证已过期或不存在，请重新发起第三方登录"));
    }

    private ThirdPartyUser toUser(ThirdBindTicket ticket) {
        ThirdPartyUser user = new ThirdPartyUser();
        user.setPlatform(ticket.getPlatform() == null ? null : ThirdPartyPlatform.of(ticket.getPlatform()));
        user.setOpenId(ticket.getOpenId());
        user.setUnionId(ticket.getUnionId());
        user.setNickname(ticket.getNickname());
        user.setAvatar(ticket.getAvatar());
        return user;
    }

    private LoginType loginType() {
        return LoginTypes.resolveOrDefault(properties.getThird().getLoginType(), LoginType.TENANT_USER.value());
    }

    private ThirdPartyPlatform resolve(String platform) {
        ThirdPartyPlatform resolved = ThirdPartyPlatform.of(platform);
        if (resolved == null) {
            throw new BizException("不支持的第三方平台：" + platform + "，支持：" + ThirdPartyPlatform.supported());
        }
        return resolved;
    }

    private String clean(String nickname) {
        if (nickname == null) {
            return null;
        }
        XssSanitizer xss = sanitizer.getIfAvailable();
        String cleaned = xss == null ? nickname.trim() : xss.clean(nickname);
        return cleaned == null || cleaned.isBlank() ? nickname : cleaned;
    }

    private String randomTicketId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(32);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
