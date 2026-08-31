package cn.slainc.idempotent.store;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseIdempotentStoreTest {

    private static final String TABLE = "t_idempotent_record_test";

    private static JdbcTemplate jdbcTemplate;

    private static DatabaseIdempotentStore store;

    @BeforeAll
    static void beforeAll() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:idempotent_store;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        store = new DatabaseIdempotentStore(jdbcTemplate, TABLE, true, new DataSourceTransactionManager(dataSource));
        store.isHeld("warmup");
    }

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM " + TABLE);
    }

    @Test
    void 并发占位只有一个赢家() throws Exception {
        String key = newKey();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                start.await();
                if (store.tryAcquire(key, Duration.ofSeconds(30))) {
                    winners.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        assertThat(winners.get()).as("主键冲突必须保证只有一个赢家").isEqualTo(1);
    }

    @Test
    void 释放后可重新占位且自动建表() {
        String key = newKey();
        assertThat(store.isHeld(key)).isFalse();
        assertThat(store.tryAcquire(key, Duration.ofSeconds(30))).isTrue();
        assertThat(store.tryAcquire(key, Duration.ofSeconds(30))).isFalse();
        store.release(key);
        assertThat(store.tryAcquire(key, Duration.ofSeconds(30))).isTrue();
        store.clear(key);
    }

    @Test
    void 结果记录可以读写() {
        String key = newKey();
        store.saveRecord(key, new IdempotentRecord(IdempotentState.SUCCESS, "java.lang.String", "\"首次\"",
                System.currentTimeMillis()), Duration.ofSeconds(30));
        IdempotentRecord loaded = store.findRecord(key).orElseThrow();
        assertThat(loaded.payload()).isEqualTo("\"首次\"");
        assertThat(store.findRecord(newKey())).isEmpty();
    }

    @Test
    void Token只能消费一次并返回归属() {
        String token = "token-" + UUID.randomUUID();
        store.saveToken(token, "2002", Duration.ofSeconds(30));
        assertThat(store.consumeToken(token)).isEqualTo("2002");
        assertThat(store.consumeToken(token)).isNull();
    }

    @Test
    void 失败计数累加且窗口过期后清零() throws Exception {
        String key = newKey();
        assertThat(store.increaseFailure(key, Duration.ofMillis(300))).isEqualTo(1L);
        assertThat(store.increaseFailure(key, Duration.ofMillis(300))).isEqualTo(2L);
        assertThat(store.failureCount(key)).isEqualTo(2L);
        Thread.sleep(400L);
        assertThat(store.failureCount(key)).as("窗口过期后失败计数应清零").isZero();
    }

    @Test
    void 表名非法时给出中文提示() {
        assertThatThrownBy(() -> new DatabaseIdempotentStore(jdbcTemplate, "t_record; drop table x", true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("幂等表名不合法");
    }

    private static String newKey() {
        return "db:" + UUID.randomUUID();
    }
}
