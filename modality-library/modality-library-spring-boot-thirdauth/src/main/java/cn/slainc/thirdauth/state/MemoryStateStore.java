package cn.slainc.thirdauth.state;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryStateStore implements StateStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int MAX_ENTRIES = 10000;

    private final Map<String, Long> states = new ConcurrentHashMap<>();

    @Override
    public String create(long ttlSeconds) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String state = HexFormat.of().formatHex(bytes);
        evict();
        states.put(state, System.currentTimeMillis() + ttlSeconds * 1000);
        return state;
    }

    @Override
    public void put(String state, long ttlSeconds) {
        if (state != null && !state.isBlank()) {
            evict();
            states.put(state, System.currentTimeMillis() + ttlSeconds * 1000);
        }
    }

    public int size() {
        return states.size();
    }

    @Override
    public boolean consume(String state) {
        if (state == null) {
            return false;
        }
        Long expireAt = states.remove(state);
        return expireAt != null && expireAt >= System.currentTimeMillis();
    }

    // 未完成的授权流程不会走到 consume，过期项必须在写入时顺手清掉，否则内存只增不减
    private void evict() {
        long now = System.currentTimeMillis();
        states.entrySet().removeIf(entry -> entry.getValue() < now);
        while (states.size() >= MAX_ENTRIES) {
            String oldest = null;
            long oldestExpireAt = Long.MAX_VALUE;
            for (Map.Entry<String, Long> entry : states.entrySet()) {
                if (entry.getValue() < oldestExpireAt) {
                    oldestExpireAt = entry.getValue();
                    oldest = entry.getKey();
                }
            }
            if (oldest == null) {
                return;
            }
            states.remove(oldest);
        }
    }
}
