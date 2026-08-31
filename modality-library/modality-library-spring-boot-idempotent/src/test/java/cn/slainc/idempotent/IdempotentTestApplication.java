package cn.slainc.idempotent;

import cn.slainc.core.result.Result;
import cn.slainc.idempotent.annotation.Idempotent;
import cn.slainc.idempotent.annotation.IdempotentStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootConfiguration
@EnableAutoConfiguration
public class IdempotentTestApplication {

    @Bean
    public OrderService orderService() {
        return new OrderService();
    }

    @Bean
    public TokenOrderService tokenOrderService() {
        return new TokenOrderService();
    }

    @Bean
    public RetryOrderService retryOrderService() {
        return new RetryOrderService();
    }

    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    public DemoOrderController demoOrderController(OrderService orderService, TokenOrderService tokenOrderService) {
        return new DemoOrderController(orderService, tokenOrderService);
    }

    @Bean
    public TokenIssueController tokenIssueController(
            cn.slainc.idempotent.token.IdempotentTokenService tokenService) {
        return new TokenIssueController(tokenService);
    }

    public record OrderRequest(String orderNo, long amount, long timestamp) {
    }

    public record TokenSubmitRequest(String token, String orderNo) {
    }

    public static class OrderService {

        private final AtomicInteger normalExecutions = new AtomicInteger();

        private final AtomicInteger slowExecutions = new AtomicInteger();

        private final AtomicInteger defaultTtlExecutions = new AtomicInteger();

        private final AtomicInteger mapExecutions = new AtomicInteger();

        private final AtomicInteger ignoreExecutions = new AtomicInteger();

        private final AtomicInteger messageExecutions = new AtomicInteger();

        private volatile CountDownLatch barrier = new CountDownLatch(0);

        @Idempotent(ttl = 120)
        public Result<String> create(OrderRequest request) {
            normalExecutions.incrementAndGet();
            return Result.ok("订单-" + request.orderNo());
        }

        @Idempotent
        public Result<String> createWithDefaultTtl(OrderRequest request) {
            defaultTtlExecutions.incrementAndGet();
            return Result.ok("默认窗口订单-" + request.orderNo());
        }

        @Idempotent(ttl = 120)
        public Result<String> createSlow(OrderRequest request) {
            slowExecutions.incrementAndGet();
            awaitBarrier();
            return Result.ok("慢订单-" + request.orderNo());
        }

        @Idempotent(ttl = 120)
        public Result<String> createByMap(Map<String, Object> payload) {
            mapExecutions.incrementAndGet();
            return Result.ok("Map订单-" + payload.get("orderNo"));
        }

        @Idempotent(ttl = 120, ignoreFields = {"timestamp"})
        public Result<String> createIgnoreTimestamp(OrderRequest request) {
            ignoreExecutions.incrementAndGet();
            return Result.ok("忽略字段订单-" + request.orderNo());
        }

        @Idempotent(ttl = 120, message = "订单重复提交，请勿重复点击")
        public Result<String> createWithMessage(OrderRequest request) {
            messageExecutions.incrementAndGet();
            awaitBarrier();
            return Result.ok("自定义提示订单-" + request.orderNo());
        }

        public void resetBarrier() {
            this.barrier = new CountDownLatch(1);
        }

        public void releaseBarrier() {
            this.barrier.countDown();
        }

        public AtomicInteger normalExecutions() {
            return normalExecutions;
        }

        public AtomicInteger slowExecutions() {
            return slowExecutions;
        }

        public AtomicInteger defaultTtlExecutions() {
            return defaultTtlExecutions;
        }

        public AtomicInteger mapExecutions() {
            return mapExecutions;
        }

        public AtomicInteger ignoreExecutions() {
            return ignoreExecutions;
        }

        public AtomicInteger messageExecutions() {
            return messageExecutions;
        }

        private void awaitBarrier() {
            try {
                barrier.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static class TokenOrderService {

        private final AtomicInteger tokenExecutions = new AtomicInteger();

        private final AtomicInteger hybridExecutions = new AtomicInteger();

        @Idempotent(strategy = IdempotentStrategy.TOKEN, ttl = 120)
        public Result<String> submitByToken(String token, String orderNo) {
            tokenExecutions.incrementAndGet();
            return Result.ok("Token订单-" + orderNo);
        }

        @Idempotent(strategy = IdempotentStrategy.HYBRID, ttl = 120)
        public Result<String> submitHybrid(String token, String orderNo) {
            hybridExecutions.incrementAndGet();
            return Result.ok("混合订单-" + orderNo);
        }

        public AtomicInteger tokenExecutions() {
            return tokenExecutions;
        }

        public AtomicInteger hybridExecutions() {
            return hybridExecutions;
        }
    }

    public static class RetryOrderService {

        private final AtomicInteger executions = new AtomicInteger();

        private final AtomicInteger remainingFailures = new AtomicInteger();

        @Idempotent(ttl = 120, maxRetries = 2, retryWindow = 60)
        public Result<String> execute(OrderRequest request) {
            executions.incrementAndGet();
            if (remainingFailures.getAndUpdate(value -> value > 0 ? value - 1 : 0) > 0) {
                throw new IllegalStateException("模拟首次执行失败");
            }
            return Result.ok("重试成功-" + request.orderNo());
        }

        @Idempotent(ttl = 120, maxRetries = 0, retryWindow = 1)
        public Result<String> executeNoRetry(OrderRequest request) {
            executions.incrementAndGet();
            if (remainingFailures.getAndUpdate(value -> value > 0 ? value - 1 : 0) > 0) {
                throw new IllegalStateException("模拟执行失败");
            }
            return Result.ok("无重试订单-" + request.orderNo());
        }

        public void failTimes(int times) {
            remainingFailures.set(times);
        }

        public AtomicInteger executions() {
            return executions;
        }
    }

}
