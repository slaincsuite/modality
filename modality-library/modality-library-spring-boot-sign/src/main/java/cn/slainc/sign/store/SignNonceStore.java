package cn.slainc.sign.store;

public interface SignNonceStore {

    boolean tryAcquire(String appId, String nonce, long ttlSeconds);

    long incrementFailure(String appId, long ttlSeconds);

    long failureCount(String appId);
}
