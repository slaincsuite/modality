package cn.slainc.monitor.satoken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Collection;

public class ReflectiveSaTokenMetricsSource implements SaTokenMetricsSource {

    private static final Logger log = LoggerFactory.getLogger(ReflectiveSaTokenMetricsSource.class);

    public static final String MANAGER_CLASS_NAME = "cn.dev33.satoken.SaManager";

    public static final String DEFAULT_TOKEN_PREFIX = "satoken:login:token:";

    public static final String DEFAULT_SESSION_PREFIX = "satoken:login:session:";

    private final String managerClassName;

    private final String tokenPrefix;

    private final String sessionPrefix;

    private final int scanLimit;

    private final long cacheMillis;

    private volatile boolean resolved;

    private volatile Class<?> managerClass;

    private volatile long onlineUsers;

    private volatile long tokenCount;

    private volatile long sampledAt;

    private volatile boolean warned;

    public ReflectiveSaTokenMetricsSource(String managerClassName, String tokenPrefix, String sessionPrefix,
                                          int scanLimit, long cacheMillis) {
        this.managerClassName = managerClassName == null || managerClassName.isBlank()
                ? MANAGER_CLASS_NAME : managerClassName;
        this.tokenPrefix = tokenPrefix == null || tokenPrefix.isBlank() ? DEFAULT_TOKEN_PREFIX : tokenPrefix;
        this.sessionPrefix = sessionPrefix == null || sessionPrefix.isBlank() ? DEFAULT_SESSION_PREFIX : sessionPrefix;
        this.scanLimit = Math.max(scanLimit, 1);
        this.cacheMillis = Math.max(cacheMillis, 0L);
    }

    public static ReflectiveSaTokenMetricsSource satoken(int scanLimit, long cacheMillis) {
        return new ReflectiveSaTokenMetricsSource(MANAGER_CLASS_NAME, DEFAULT_TOKEN_PREFIX, DEFAULT_SESSION_PREFIX,
                scanLimit, cacheMillis);
    }

    @Override
    public long onlineUsers() {
        sampleIfStale();
        return onlineUsers;
    }

    @Override
    public long tokenCount() {
        sampleIfStale();
        return tokenCount;
    }

    public boolean available() {
        return managerClass() != null;
    }

    private void sampleIfStale() {
        if (System.currentTimeMillis() - sampledAt < cacheMillis) {
            return;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - sampledAt < cacheMillis) {
                return;
            }
            try {
                Class<?> type = managerClass();
                if (type == null) {
                    return;
                }
                Object dao = type.getMethod("getSaTokenDao").invoke(null);
                if (dao == null) {
                    return;
                }
                Method search = dao.getClass().getMethod("searchData", String.class, String.class, int.class,
                        int.class, boolean.class);
                onlineUsers = count(search, dao, sessionPrefix);
                tokenCount = count(search, dao, tokenPrefix);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                if (!warned) {
                    warned = true;
                    log.warn("Sa-Token 在线用户/Token 数采集失败，Gauge 将保持上一次取值: {}", exception.getMessage());
                }
            } finally {
                sampledAt = System.currentTimeMillis();
            }
        }
    }

    private long count(Method search, Object dao, String prefix) throws ReflectiveOperationException {
        Object value = search.invoke(dao, prefix, "", 0, scanLimit, false);
        return value instanceof Collection<?> collection ? collection.size() : 0L;
    }

    private Class<?> managerClass() {
        if (resolved) {
            return managerClass;
        }
        synchronized (this) {
            if (!resolved) {
                try {
                    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                    managerClass = Class.forName(managerClassName, false,
                            classLoader == null ? ReflectiveSaTokenMetricsSource.class.getClassLoader() : classLoader);
                } catch (ClassNotFoundException | LinkageError exception) {
                    managerClass = null;
                }
                resolved = true;
            }
        }
        return managerClass;
    }
}
