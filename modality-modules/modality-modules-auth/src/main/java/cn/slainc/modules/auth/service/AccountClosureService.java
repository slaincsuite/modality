package cn.slainc.modules.auth.service;
import cn.slainc.modules.auth.closure.AccountClosureRecord;
import cn.slainc.modules.auth.store.AccountClosureStore;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.auth.config.AuthProperties;
import cn.slainc.security.StpKit;
import cn.slainc.security.spi.AccountAdminPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

// 账号注销：申请进入 7 天冷静期，到期由调度器软删除（数据保留），期间用户可随时撤销
public class AccountClosureService {

    private static final Logger log = LoggerFactory.getLogger(AccountClosureService.class);

    private final AccountClosureStore store;

    private final AuthProperties properties;

    private final ObjectProvider<AccountAdminPort> adminPort;

    public AccountClosureService(AccountClosureStore store, AuthProperties properties,
                                 ObjectProvider<AccountAdminPort> adminPort) {
        this.store = store;
        this.properties = properties;
        this.adminPort = adminPort;
    }

    public AccountClosureRecord apply(LoginType type, Long userId, String username, String reason) {
        if (store.findPending(userId).isPresent()) {
            throw new BizException("已存在待执行的注销申请，冷静期内可撤销");
        }
        long now = System.currentTimeMillis();
        AccountClosureRecord record = new AccountClosureRecord();
        record.setTenantId(resolveTenantId(type, userId));
        record.setUserId(userId);
        record.setLoginType(type.value());
        record.setUsername(username);
        record.setReason(reason);
        record.setStatus(AccountClosureRecord.STATUS_PENDING);
        record.setRequestedAt(now);
        record.setCoolDownUntil(now + properties.getClosure().getCoolDownSeconds() * 1000);
        return store.insert(record);
    }

    public void cancel(Long userId) {
        AccountClosureRecord pending = store.findPending(userId)
                .orElseThrow(() -> new BizException("没有待执行的注销申请，无需撤销"));
        store.updateStatus(pending.getId(), AccountClosureRecord.STATUS_CANCELLED, System.currentTimeMillis(), null);
    }

    public Optional<AccountClosureRecord> latestStatus(Long userId) {
        return store.findLatest(userId);
    }

    private Long resolveTenantId(LoginType type, Long userId) {
        if (type != LoginType.TENANT_USER) {
            return null;
        }
        AccountAdminPort port = adminPort.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException("未接入账号数据源，无法记录注销申请的租户归属");
        }
        return port.tenantIdOf(userId);
    }

    public int executeDue() {
        List<AccountClosureRecord> due = store.findDue(System.currentTimeMillis(), 100);
        int executed = 0;
        for (AccountClosureRecord record : due) {
            try {
                AccountAdminPort port = adminPort.getIfAvailable();
                if (port == null) {
                    throw new IllegalStateException("未接入账号数据源，无法执行注销");
                }
                port.delete(record.getUserId());
                LoginType type = LoginType.of(record.getLoginType());
                if (type != null) {
                    StpKit.of(type).logout(record.getUserId());
                }
                store.updateStatus(record.getId(), AccountClosureRecord.STATUS_EXECUTED,
                        System.currentTimeMillis(), null);
                executed++;
                log.info("账号注销已执行：userId={}，loginType={}", record.getUserId(), record.getLoginType());
            } catch (Exception e) {
                store.updateStatus(record.getId(), AccountClosureRecord.STATUS_FAILED, System.currentTimeMillis(),
                        e.getMessage());
                log.warn("账号注销执行失败：userId={}，原因：{}", record.getUserId(), e.getMessage());
            }
        }
        return executed;
    }
}
