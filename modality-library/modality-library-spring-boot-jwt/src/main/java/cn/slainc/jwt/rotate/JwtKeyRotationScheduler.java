package cn.slainc.jwt.rotate;

import cn.slainc.jwt.key.JwtKeyManager;
import cn.slainc.utils.thread.ThreadPoolUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class JwtKeyRotationScheduler implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyRotationScheduler.class);

    private final JwtKeyManager keyManager;

    private final Duration interval;

    private ScheduledExecutorService executor;

    public JwtKeyRotationScheduler(JwtKeyManager keyManager, Duration interval) {
        this.keyManager = keyManager;
        this.interval = interval;
    }

    @Override
    public void afterPropertiesSet() {
        executor = ThreadPoolUtils.newDaemonSingleThreadScheduler("jwt-key-rotation");
        long delayMillis = Math.max(1, interval.toMillis());
        executor.scheduleWithFixedDelay(this::rotateSafely, delayMillis, delayMillis, TimeUnit.MILLISECONDS);
        log.info("jwt 密钥自动轮换已启动，周期：{}，首次轮换延迟一个周期执行", interval);
    }

    private void rotateSafely() {
        try {
            keyManager.rotate();
        } catch (RuntimeException exception) {
            log.error("jwt 密钥自动轮换失败，将在下个周期重试", exception);
        }
    }

    @Override
    public void destroy() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
