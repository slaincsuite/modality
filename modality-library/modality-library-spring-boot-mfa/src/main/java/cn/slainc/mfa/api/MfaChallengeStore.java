package cn.slainc.mfa.api;

import cn.slainc.mfa.challenge.MfaChallenge;

import java.time.Duration;

public interface MfaChallengeStore {

    void save(MfaChallenge challenge, Duration ttl);

    MfaChallenge consume(String challengeId);

    MfaChallenge peek(String challengeId);
}
