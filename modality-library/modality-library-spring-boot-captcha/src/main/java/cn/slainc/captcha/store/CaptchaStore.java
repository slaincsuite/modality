package cn.slainc.captcha.store;

import java.time.Duration;

public interface CaptchaStore {

    void put(String key, String value, Duration ttl);

    String get(String key);

    void remove(String key);

    long failCount(String key);

    long incrementFail(String key, Duration ttl);

    boolean isLocked(String key);

    void lock(String key, Duration ttl);
}
