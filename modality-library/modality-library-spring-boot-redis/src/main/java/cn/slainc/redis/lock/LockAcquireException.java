package cn.slainc.redis.lock;

public class LockAcquireException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LockAcquireException(String message) {
        super(message);
    }

    public LockAcquireException(String message, Throwable cause) {
        super(message, cause);
    }
}
