package cn.slainc.security.dao;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.session.SaSession;
import cn.slainc.security.config.SecurityKeys;
import cn.slainc.utils.thread.ThreadPoolUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

public class DegradableSaTokenDao implements SaTokenDao, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DegradableSaTokenDao.class);

    private final SaTokenDao delegate;

    private final SaTokenDao memory = new SaTokenDaoDefaultImpl();

    private final long retrySeconds;

    private final AtomicBoolean warnOnce = new AtomicBoolean();

    private volatile boolean degraded;

    private volatile ScheduledExecutorService probeScheduler;

    public DegradableSaTokenDao(SaTokenDao delegate, long retrySeconds) {
        if (delegate == null) {
            throw new IllegalArgumentException("被委托的 SaTokenDao 不能为空");
        }
        this.delegate = delegate;
        this.retrySeconds = Math.max(retrySeconds, 1);
        memory.init();
    }

    public boolean isDegraded() {
        return degraded;
    }

    public SaTokenDao currentTarget() {
        return degraded ? memory : delegate;
    }

    public synchronized void probeAndRecover() {
        try {
            delegate.getTimeout(SecurityKeys.DAO_PROBE_KEY);
        } catch (DataAccessException e) {
            if (!degraded) {
                degrade(e);
            }
            return;
        } catch (Exception e) {
            log.debug("探测 Redis 异常且不属于连接类失败，保持当前模式：{}", e.getMessage());
            return;
        }
        if (degraded) {
            recover();
        }
    }

    private synchronized void degrade(DataAccessException cause) {
        if (degraded) {
            return;
        }
        degraded = true;
        if (warnOnce.compareAndSet(false, true)) {
            log.warn("Redis 访问失败，Sa-Token 持久层已降级为内存模式（会话数据不跨节点共享，恢复后自动切回）：{}",
                    cause.getMessage());
        }
        startProbeScheduler();
    }

    private synchronized void recover() {
        degraded = false;
        warnOnce.set(false);
        stopProbeScheduler();
        log.info("Redis 已恢复，Sa-Token 持久层已切回 Redis 模式");
    }

    private synchronized void startProbeScheduler() {
        if (probeScheduler != null) {
            return;
        }
        ScheduledExecutorService scheduler = ThreadPoolUtils.newDaemonSingleThreadScheduler("security-satoken-dao-probe");
        scheduler.scheduleWithFixedDelay(this::probeQuietly, retrySeconds, retrySeconds, TimeUnit.SECONDS);
        probeScheduler = scheduler;
    }

    private synchronized void stopProbeScheduler() {
        ScheduledExecutorService scheduler = probeScheduler;
        probeScheduler = null;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void probeQuietly() {
        try {
            probeAndRecover();
        } catch (Exception e) {
            log.debug("周期探测 Redis 出现未预期异常：{}", e.getMessage());
        }
    }

    private <T> T read(Function<SaTokenDao, T> action) {
        if (!degraded) {
            try {
                return action.apply(delegate);
            } catch (DataAccessException e) {
                degrade(e);
            }
        }
        return action.apply(memory);
    }

    private void write(Consumer<SaTokenDao> action) {
        if (!degraded) {
            try {
                action.accept(delegate);
                return;
            } catch (DataAccessException e) {
                degrade(e);
            }
        }
        action.accept(memory);
    }

    @Override
    public String get(String key) {
        return read(dao -> dao.get(key));
    }

    @Override
    public void set(String key, String value, long timeout) {
        write(dao -> dao.set(key, value, timeout));
    }

    @Override
    public void update(String key, String value) {
        write(dao -> dao.update(key, value));
    }

    @Override
    public void delete(String key) {
        write(dao -> dao.delete(key));
    }

    @Override
    public long getTimeout(String key) {
        return read(dao -> dao.getTimeout(key));
    }

    @Override
    public void updateTimeout(String key, long timeout) {
        write(dao -> dao.updateTimeout(key, timeout));
    }

    @Override
    public Object getObject(String key) {
        return read(dao -> dao.getObject(key));
    }

    @Override
    public <T> T getObject(String key, Class<T> classType) {
        return read(dao -> dao.getObject(key, classType));
    }

    @Override
    public void setObject(String key, Object object, long timeout) {
        write(dao -> dao.setObject(key, object, timeout));
    }

    @Override
    public void updateObject(String key, Object object) {
        write(dao -> dao.updateObject(key, object));
    }

    @Override
    public void deleteObject(String key) {
        write(dao -> dao.deleteObject(key));
    }

    @Override
    public long getObjectTimeout(String key) {
        return read(dao -> dao.getObjectTimeout(key));
    }

    @Override
    public void updateObjectTimeout(String key, long timeout) {
        write(dao -> dao.updateObjectTimeout(key, timeout));
    }

    @Override
    public SaSession getSession(String sessionId) {
        return read(dao -> dao.getSession(sessionId));
    }

    @Override
    public void setSession(SaSession session, long timeout) {
        write(dao -> dao.setSession(session, timeout));
    }

    @Override
    public void updateSession(SaSession session) {
        write(dao -> dao.updateSession(session));
    }

    @Override
    public void deleteSession(String sessionId) {
        write(dao -> dao.deleteSession(sessionId));
    }

    @Override
    public long getSessionTimeout(String sessionId) {
        return read(dao -> dao.getSessionTimeout(sessionId));
    }

    @Override
    public void updateSessionTimeout(String sessionId, long timeout) {
        write(dao -> dao.updateSessionTimeout(sessionId, timeout));
    }

    @Override
    public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
        return read(dao -> dao.searchData(prefix, keyword, start, size, sortType));
    }

    @Override
    public void init() {
        delegate.init();
    }

    @Override
    public void close() {
        stopProbeScheduler();
        memory.destroy();
        try {
            delegate.destroy();
        } catch (Exception e) {
            log.debug("关闭委托持久层时出现异常：{}", e.getMessage());
        }
    }
}
