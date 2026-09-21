package cn.slainc.security;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import cn.slainc.security.dao.DegradableSaTokenDao;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DegradableSaTokenDaoTest {

    private FlakyRedisDao delegate;

    private DegradableSaTokenDao dao;

    private Logger logger;

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        delegate = new FlakyRedisDao();
        dao = new DegradableSaTokenDao(delegate, 1);
        logger = (Logger) LoggerFactory.getLogger(DegradableSaTokenDao.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        dao.close();
        logger.detachAppender(logs);
    }

    @Test
    void delegatesToRedisWhenHealthy() {
        dao.set("k1", "v1", 60);
        assertThat(dao.get("k1")).isEqualTo("v1");
        assertThat(delegate.stringStore.get("k1")).isEqualTo("v1");
        assertThat(dao.isDegraded()).isFalse();
    }

    @Test
    void degradesToMemoryWhenRedisFails() {
        delegate.fail = true;
        dao.set("k2", "v2", 60);
        assertThat(dao.isDegraded()).isTrue();
        assertThat(dao.get("k2")).isEqualTo("v2");
        assertThat(delegate.stringStore).doesNotContainKey("k2");
    }

    @Test
    void degradeWarnsOnlyOnce() {
        delegate.fail = true;
        dao.set("k3", "v3", 60);
        dao.get("k3");
        dao.updateTimeout("k3", 120);
        long warns = logs.list.stream().filter(event -> event.getFormattedMessage().contains("降级为内存模式")).count();
        assertThat(warns).isEqualTo(1);
    }

    @Test
    void probeRecoversWhenRedisBackOnline() {
        delegate.fail = true;
        dao.set("k4", "v4", 60);
        assertThat(dao.isDegraded()).isTrue();
        delegate.fail = false;
        dao.probeAndRecover();
        assertThat(dao.isDegraded()).isFalse();
        dao.set("k5", "v5", 60);
        assertThat(delegate.stringStore.get("k5")).isEqualTo("v5");
    }

    @Test
    void probeDoesNothingWhenHealthyAndNotDegraded() {
        dao.probeAndRecover();
        assertThat(dao.isDegraded()).isFalse();
    }

    @Test
    void noSchedulerStartedWhileHealthy() {
        assertThat(dao.isDegraded()).isFalse();
        dao.set("k6", "v6", 60);
        assertThat(dao.isDegraded()).isFalse();
    }

    @Test
    void memoryModeSupportsSessionAndSearch() {
        delegate.fail = true;
        SaSession session = new SaSession("session-1");
        session.set("attr", "value");
        dao.setSession(session, 60);
        SaSession loaded = dao.getSession("session-1");
        assertThat(loaded).isNotNull();
        assertThat(loaded.get("attr")).isEqualTo("value");
        dao.set("satoken:TENANT_USER:token:abc", "2001", 60);
        List<String> found = dao.searchData("satoken", "abc", 0, 10, true);
        assertThat(found).anyMatch(key -> key.endsWith("abc"));
    }

    @Test
    void timeoutRoundTripDelegates() {
        dao.set("k7", "v7", 120);
        assertThat(dao.getTimeout("k7")).isBetween(100L, 120L);
        dao.updateTimeout("k7", 200);
        assertThat(dao.getTimeout("k7")).isBetween(150L, 200L);
        assertThat(dao.getTimeout("missing-key")).isEqualTo(SaTokenDao.NOT_VALUE_EXPIRE);
    }

    @Test
    void objectRoundTripDelegates() {
        dao.setObject("obj-1", "plain-object", 60);
        assertThat(dao.getObject("obj-1")).isEqualTo("plain-object");
        dao.updateObject("obj-1", "updated-object");
        assertThat(dao.getObject("obj-1")).isEqualTo("updated-object");
        dao.deleteObject("obj-1");
        assertThat(dao.getObject("obj-1")).isNull();
    }

    @Test
    void closeShutsDownWithoutError() {
        delegate.fail = true;
        dao.set("k8", "v8", 60);
        dao.close();
        assertThat(dao.isDegraded()).isTrue();
    }

    @Test
    void recoveryResetsWarnFlagForNextOutage() {
        delegate.fail = true;
        dao.set("k9", "v9", 60);
        delegate.fail = false;
        dao.probeAndRecover();
        delegate.fail = true;
        dao.set("k10", "v10", 60);
        long warns = logs.list.stream().filter(event -> event.getFormattedMessage().contains("降级为内存模式")).count();
        assertThat(warns).isEqualTo(2);
    }

    static class FlakyRedisDao implements SaTokenDao {

        final java.util.Map<String, String> stringStore = new java.util.concurrent.ConcurrentHashMap<>();

        final java.util.Map<String, Object> objectStore = new java.util.concurrent.ConcurrentHashMap<>();

        final java.util.Map<String, Long> expiry = new java.util.concurrent.ConcurrentHashMap<>();

        volatile boolean fail;

        private void check() {
            if (fail) {
                throw new DataAccessResourceFailureException("模拟 Redis 连接失败");
            }
        }

        @Override
        public String get(String key) {
            check();
            return stringStore.get(key);
        }

        @Override
        public void set(String key, String value, long timeout) {
            check();
            stringStore.put(key, value);
            storeTimeout(key, timeout);
        }

        @Override
        public void update(String key, String value) {
            check();
            stringStore.put(key, value);
        }

        @Override
        public void delete(String key) {
            check();
            stringStore.remove(key);
            expiry.remove(key);
        }

        @Override
        public long getTimeout(String key) {
            check();
            if (!stringStore.containsKey(key)) {
                return SaTokenDao.NOT_VALUE_EXPIRE;
            }
            Long expireAt = expiry.get(key);
            if (expireAt == null) {
                return SaTokenDao.NEVER_EXPIRE;
            }
            long remain = (expireAt - System.currentTimeMillis()) / 1000;
            return remain <= 0 ? SaTokenDao.NOT_VALUE_EXPIRE : remain;
        }

        @Override
        public void updateTimeout(String key, long timeout) {
            check();
            storeTimeout(key, timeout);
        }

        private void storeTimeout(String key, long timeout) {
            if (timeout <= 0) {
                expiry.remove(key);
                return;
            }
            expiry.put(key, System.currentTimeMillis() + timeout * 1000);
        }

        @Override
        public Object getObject(String key) {
            check();
            return objectStore.get(key);
        }

        @Override
        public <T> T getObject(String key, Class<T> classType) {
            return classType.cast(getObject(key));
        }

        @Override
        public void setObject(String key, Object object, long timeout) {
            check();
            objectStore.put(key, object);
        }

        @Override
        public void updateObject(String key, Object object) {
            check();
            objectStore.put(key, object);
        }

        @Override
        public void deleteObject(String key) {
            check();
            objectStore.remove(key);
        }

        @Override
        public long getObjectTimeout(String key) {
            check();
            return objectStore.containsKey(key) ? SaTokenDao.NEVER_EXPIRE : SaTokenDao.NOT_VALUE_EXPIRE;
        }

        @Override
        public void updateObjectTimeout(String key, long timeout) {
            check();
        }

        @Override
        public SaSession getSession(String sessionId) {
            return (SaSession) getObject(sessionId);
        }

        @Override
        public void setSession(SaSession session, long timeout) {
            setObject(sessionId(session), session, timeout);
        }

        @Override
        public void updateSession(SaSession session) {
            updateObject(sessionId(session), session);
        }

        @Override
        public void deleteSession(String sessionId) {
            deleteObject(sessionId);
        }

        @Override
        public long getSessionTimeout(String sessionId) {
            return getObjectTimeout(sessionId);
        }

        @Override
        public void updateSessionTimeout(String sessionId, long timeout) {
            updateObjectTimeout(sessionId, timeout);
        }

        private String sessionId(SaSession session) {
            return session.getId();
        }

        @Override
        public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
            check();
            return stringStore.keySet().stream().filter(key -> key.startsWith(prefix))
                    .filter(key -> keyword == null || key.contains(keyword)).toList();
        }
    }
}
