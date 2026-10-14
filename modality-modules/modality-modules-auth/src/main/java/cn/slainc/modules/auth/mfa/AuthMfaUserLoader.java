package cn.slainc.modules.auth.mfa;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.mfa.spi.MfaUserLoader;
import cn.slainc.modules.auth.store.MfaFactorStore;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.PasswordAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;

// auth 模块不直接依赖 upms：用户名与账号体系经 security 库的 AccountAuthProvider 端口读取
public class AuthMfaUserLoader implements MfaUserLoader {

    public static final String FACTOR_TYPE_TOTP = "TOTP";

    public static final List<LoginType> USER_LOGIN_TYPES = List.of(LoginType.PLATFORM_ADMIN, LoginType.PLATFORM_USER,
            LoginType.TENANT_ADMIN, LoginType.TENANT_USER);

    private static final Logger log = LoggerFactory.getLogger(AuthMfaUserLoader.class);

    private final ObjectProvider<AccountAuthProvider> authProvider;

    private final MfaFactorStore factorStore;

    public AuthMfaUserLoader(ObjectProvider<AccountAuthProvider> authProvider, MfaFactorStore factorStore) {
        this.authProvider = authProvider;
        this.factorStore = factorStore;
    }

    @Override
    public UserContext.UserInfo loadByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        AccountAuthProvider provider = authProvider == null ? null : authProvider.getIfAvailable();
        if (provider == null) {
            return null;
        }
        for (LoginType type : orderedTypes(userId)) {
            try {
                PasswordAccount account = provider.findAccountByUserId(userId, type);
                if (account != null && account.enabled()) {
                    return UserContext.UserInfo.of(userId, account.username(), type);
                }
            } catch (RuntimeException e) {
                log.warn("按账号体系 {} 装载用户 {} 失败：{}", type, userId, e.getMessage());
            }
        }
        return null;
    }

    private List<LoginType> orderedTypes(Long userId) {
        LoginType preferredType = null;
        try {
            MfaFactorRecord record = factorStore.findRecord(userId, FACTOR_TYPE_TOTP);
            preferredType = record == null || record.getLoginType() == null ? null : LoginType.of(record.getLoginType());
        } catch (RuntimeException e) {
            log.warn("读取二次验证因子所属账号体系失败 userId={}：{}", userId, e.getMessage());
        }
        List<LoginType> ordered = new ArrayList<>(USER_LOGIN_TYPES.size());
        if (preferredType != null) {
            ordered.add(preferredType);
        }
        for (LoginType type : USER_LOGIN_TYPES) {
            if (type != preferredType) {
                ordered.add(type);
            }
        }
        return ordered;
    }
}
