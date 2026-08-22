package cn.slainc.redis.dao;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RedisSaTokenDao implements SaTokenDao {

    private static final Logger log = LoggerFactory.getLogger(RedisSaTokenDao.class);

    private final SaTokenRedisStore store;

    public RedisSaTokenDao(SaTokenRedisStore store) {
        if (store == null) {
            throw new IllegalArgumentException("SaTokenRedisStore 不能为空");
        }
        this.store = store;
    }

    public SaTokenRedisStore getStore() {
        return store;
    }

    @Override
    public String get(String key) {
        return store.get(key);
    }

    @Override
    public void set(String key, String value, long timeout) {
        store.set(key, value, timeout);
    }

    @Override
    public void update(String key, String value) {
        store.update(key, value);
    }

    @Override
    public void delete(String key) {
        store.delete(key);
    }

    @Override
    public long getTimeout(String key) {
        return store.getTimeout(key);
    }

    @Override
    public void updateTimeout(String key, long timeout) {
        store.updateTimeout(key, timeout);
    }

    @Override
    public Object getObject(String key) {
        return store.getObject(key);
    }

    @Override
    public <T> T getObject(String key, Class<T> classType) {
        return store.getObject(key, classType);
    }

    @Override
    public void setObject(String key, Object object, long timeout) {
        store.setObject(key, object, timeout);
    }

    @Override
    public void updateObject(String key, Object object) {
        store.updateObject(key, object);
    }

    @Override
    public void deleteObject(String key) {
        store.deleteObject(key);
    }

    @Override
    public long getObjectTimeout(String key) {
        return store.getObjectTimeout(key);
    }

    @Override
    public void updateObjectTimeout(String key, long timeout) {
        store.updateObjectTimeout(key, timeout);
    }

    @Override
    public SaSession getSession(String sessionId) {
        return store.getObject(sessionId, SaSession.class);
    }

    @Override
    public void setSession(SaSession session, long timeout) {
        if (session == null) {
            throw new IllegalArgumentException("SaSession 不能为空");
        }
        store.setObject(session.getId(), session, timeout);
    }

    @Override
    public void updateSession(SaSession session) {
        if (session == null) {
            throw new IllegalArgumentException("SaSession 不能为空");
        }
        store.updateObject(session.getId(), session);
    }

    @Override
    public void deleteSession(String sessionId) {
        store.deleteObject(sessionId);
    }

    @Override
    public long getSessionTimeout(String sessionId) {
        return store.getObjectTimeout(sessionId);
    }

    @Override
    public void updateSessionTimeout(String sessionId, long timeout) {
        store.updateObjectTimeout(sessionId, timeout);
    }

    @Override
    public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
        return store.searchData(prefix, keyword, start, size, sortType);
    }

    @Override
    public void init() {
        log.info("Sa-Token Redis 持久层已初始化");
    }

    @Override
    public void destroy() {
        log.info("Sa-Token Redis 持久层已销毁");
    }
}
