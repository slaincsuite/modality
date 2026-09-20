package cn.slainc.thirdauth.state;

import cn.slainc.thirdauth.ThirdAuthTestApplication;
import cn.slainc.thirdauth.config.ThirdAuthKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = ThirdAuthTestApplication.class)
class StateStoreRedisTest {

    @Autowired
    private StateStore stateStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void stateConsumedExactlyOnce() {
        String state = stateStore.create(60);
        assertTrue(redisTemplate.hasKey(ThirdAuthKeys.stateKey(state)));
        assertTrue(stateStore.consume(state));
        assertFalse(stateStore.consume(state));
        assertNull(redisTemplate.opsForValue().get(ThirdAuthKeys.stateKey(state)));
    }

    @Test
    void customStateRegisteredAndConsumed() {
        stateStore.put("custom-state-abc", 60);
        assertTrue(stateStore.consume("custom-state-abc"));
        assertFalse(stateStore.consume("custom-state-abc"));
    }

    @Test
    void forgedAndNullStateRejected() {
        assertFalse(stateStore.consume("forged-state"));
        assertFalse(stateStore.consume(null));
    }

    @Test
    void stateKeyHasTtl() {
        String state = stateStore.create(120);
        Long ttl = redisTemplate.getExpire(ThirdAuthKeys.stateKey(state), TimeUnit.SECONDS);
        assertTrue(ttl != null && ttl > 0 && ttl <= 120);
        assertTrue(stateStore.consume(state));
    }
}
