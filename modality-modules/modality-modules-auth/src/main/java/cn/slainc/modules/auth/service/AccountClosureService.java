package cn.slainc.modules.auth.service;
import cn.slainc.modules.auth.closure.AccountClosureRecord;
import cn.slainc.modules.auth.store.AccountClosureStore;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.auth.config.AuthProperties;
import cn.slainc.security.StpKit;
import cn.slainc.security.audit.SecurityAuditRecorder;
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

    private final ObjectProvider<SecurityAuditRecorder> recorder;

    public AccountClosureService(AccountClosureStore store, AuthProperties properties,
                                 ObjectProvider<AccountAdminPort> adminPort) {
        this(store, properties, adminPort, null);
    }

    public AccountClosureService(AccountClosureStore store, AuthProperties properties,
                                 ObjectProvider<AccountAdminPort> adminPort,
                                 ObjectProvider<SecurityAuditRecorder> recorder) {
        this.store = store;
        this.properties = properties;
        this.adminPort = adminPort;
        this.recorder = recorder;
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
        return store.insertIfAbsent(record)
                .orElseThrow(() -> new BizException("已存在待执行的注销申请，冷静期内可撤销"));
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
                handleFailure(record, e);
            }
        }
        return executed;
    }

    // 单次失败不能直接置 FAILED（否则永不重试）：未达上限时保持 PENDING 并按退避顺延 cool_down_until
    private void handleFailure(AccountClosureRecord record, Exception cause) {
        int attempts = record.getAttemptCount() + 1;
        long now = System.currentTimeMillis();
        String error = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        int maxAttempts = Math.max(1, properties.getClosure().getMaxExecuteAttempts());
        if (attempts < maxAttempts) {
            store.markRetry(record.getId(), attempts, now + properties.getClosure().getRetryBackoffSeconds() * 1000,
                    error);
            log.warn("账号注销执行失败，将在退避后重试（第 {} 次，共 {} 次）：userId={}，原因：{}", attempts, maxAttempts,
                    record.getUserId(), error);
            return;
        }
        store.markFailed(record.getId(), attempts, error);
        log.error("账号注销执行失败已达重试上限（{} 次），已置为 FAILED：userId={}，原因：{}", maxAttempts,
                record.getUserId(), error);
        SecurityAuditRecorder audit = recorder == null ? null : recorder.getIfAvailable();
        if (audit != null) {
            audit.alarm("账号注销执行失败", LoginType.of(record.getLoginType()), record.getUserId(), null,
                    "连续 " + maxAttempts + " 次执行失败：" + error);
        }
    }
}
