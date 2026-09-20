package cn.slainc.thirdauth.state;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryStateStore implements StateStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Long> states = new ConcurrentHashMap<>();

    @Override
    public String create(long ttlSeconds) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String state = HexFormat.of().formatHex(bytes);
        states.put(state, System.currentTimeMillis() + ttlSeconds * 1000);
        return state;
    }

    @Override
    public void put(String state, long ttlSeconds) {
        if (state != null && !state.isBlank()) {
            states.put(state, System.currentTimeMillis() + ttlSeconds * 1000);
        }
    }

    @Override
    public boolean consume(String state) {
        if (state == null) {
            return false;
        }
        Long expireAt = states.remove(state);
        return expireAt != null && expireAt >= System.currentTimeMillis();
    }
}
