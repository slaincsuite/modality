package cn.slainc.webhook.support;

import cn.slainc.core.constant.CacheKeys;
import cn.slainc.redis.lock.DistributedLockTemplate;
import cn.slainc.redis.lock.LockHandle;
import cn.slainc.redis.lock.LockType;
import cn.slainc.webhook.config.WebhookProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;

public class WebhookRetryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookRetryScheduler.class);

    public static final String LOCK_KEY = CacheKeys.LOCK + "webhook:retry";

    private static final long LOCK_WAIT_SECONDS = 0L;

    private static final long LOCK_LEASE_SECONDS = Duration.ofMinutes(5).toSeconds();

    private final WebhookDeliveryDispatcher dispatcher;

    private final WebhookProperties properties;

    private final ObjectProvider<DistributedLockTemplate> lockProvider;

    private final ThreadPoolTaskScheduler scheduler;

    public WebhookRetryScheduler(WebhookDeliveryDispatcher dispatcher, WebhookProperties properties,
                                 ObjectProvider<DistributedLockTemplate> lockProvider,
                                 ThreadPoolTaskScheduler scheduler) {
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.lockProvider = lockProvider;
        this.scheduler = scheduler;
    }

    public void start() {
        Duration interval = properties.getRetryInterval();
        if (scheduler == null || interval == null || interval.isZero() || interval.isNegative()) {
            LOGGER.debug("Webhook 重投调度未启用（interval={}）", interval);
            return;
        }
        scheduler.scheduleWithFixedDelay(this::retryDue, interval);
    }

    public int retryDue() {
        DistributedLockTemplate lockTemplate = resolveLockTemplate();
        if (lockTemplate == null) {
            return doRetry();
        }
        LockHandle handle = acquireQuietly(lockTemplate);
        if (handle == null) {
            // 拿锁异常时宁可本节点照常重投，也不能停摆
            return doRetry();
        }
        if (!handle.isLocked()) {
            LOGGER.debug("其他节点正在重投 Webhook，本节点跳过本轮");
            return 0;
        }
        try {
            return doRetry();
        } finally {
            closeQuietly(handle);
        }
    }

    private DistributedLockTemplate resolveLockTemplate() {
        if (lockProvider == null) {
            return null;
        }
        try {
            return lockProvider.getIfAvailable();
        } catch (RuntimeException e) {
            LOGGER.warn("分布式锁不可用，Webhook 重投退化为单节点执行：{}", e.getMessage());
            return null;
        }
    }

    private LockHandle acquireQuietly(DistributedLockTemplate lockTemplate) {
        try {
            return lockTemplate.acquire(LOCK_KEY, LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, LockType.REENTRANT);
        } catch (RuntimeException e) {
            LOGGER.warn("获取 Webhook 重投锁失败，退化为本节点执行：{}", e.getMessage());
            return null;
        }
    }

    private void closeQuietly(LockHandle handle) {
        try {
            handle.close();
        } catch (RuntimeException e) {
            LOGGER.debug("释放 Webhook 重投锁失败：{}", e.getMessage());
        }
    }

    private int doRetry() {
        try {
            int retried = dispatcher.retryDue(Math.max(properties.getBatchSize(), 1));
            if (retried > 0) {
                LOGGER.info("本轮重投了 {} 条 Webhook 投递", retried);
            }
            return retried;
        } catch (RuntimeException e) {
            LOGGER.error("重投 Webhook 失败：{}", e.getMessage(), e);
            return 0;
        }
    }
}
