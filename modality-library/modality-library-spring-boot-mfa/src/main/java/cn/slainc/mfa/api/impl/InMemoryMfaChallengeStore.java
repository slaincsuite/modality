package cn.slainc.mfa.api.impl;

import cn.slainc.mfa.api.MfaChallengeStore;
import cn.slainc.mfa.challenge.MfaChallenge;
import cn.slainc.utils.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class InMemoryMfaChallengeStore implements MfaChallengeStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryMfaChallengeStore.class);

    private static final int DEFAULT_MAX_ENTRIES = 10000;

    private static final int CLEANUP_THRESHOLD = 64;

    private final int maxEntries;

    private final ConcurrentHashMap<String, MfaChallenge> challenges = new ConcurrentHashMap<>();

    private final AtomicBoolean degraded = new AtomicBoolean();

    public InMemoryMfaChallengeStore() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public InMemoryMfaChallengeStore(int maxEntries) {
        this.maxEntries = Math.max(maxEntries, 16);
    }

    @Override
    public void save(MfaChallenge challenge, Duration ttl) {
        if (challenge == null || StringUtils.isBlank(challenge.id())) {
            return;
        }
        if (degraded.compareAndSet(false, true)) {
            log.warn("modality.mfa：二次验证挑战保存在进程内存中，多实例部署需要 Redis 共享");
        }
        MfaChallenge stored = challenge.expiresAt() > 0L ? challenge
                : MfaChallenge.of(challenge.id(), challenge.userId(), challenge.username(), challenge.method(), ttl);
        challenges.put(stored.id(), stored);
        cleanup();
    }

    @Override
    public MfaChallenge consume(String challengeId) {
        if (StringUtils.isBlank(challengeId)) {
            return null;
        }
        return valid(challenges.remove(challengeId));
    }

    @Override
    public MfaChallenge peek(String challengeId) {
        if (StringUtils.isBlank(challengeId)) {
            return null;
        }
        return valid(challenges.get(challengeId));
    }

    public int size() {
        return challenges.size();
    }

    private MfaChallenge valid(MfaChallenge challenge) {
        return challenge == null || challenge.expired() ? null : challenge;
    }

    private void cleanup() {
        if (challenges.size() >= CLEANUP_THRESHOLD || challenges.size() > maxEntries) {
            long now = System.currentTimeMillis();
            challenges.entrySet().removeIf(entry -> entry.getValue().expiredAt(now));
        }
        if (challenges.size() <= maxEntries) {
            return;
        }
        Iterator<String> iterator = challenges.keySet().iterator();
        while (challenges.size() > maxEntries && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }
}
