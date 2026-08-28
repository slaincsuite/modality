package cn.slainc.idempotent.store;

import cn.slainc.idempotent.support.IdempotentKeys;
import cn.slainc.utils.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class DatabaseIdempotentStore implements IdempotentStore {

    private static final Logger log = LoggerFactory.getLogger(DatabaseIdempotentStore.class);

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_.]+");

    private static final int MAX_PAYLOAD_LENGTH = 60_000;

    private final JdbcTemplate jdbcTemplate;

    private final String table;

    private final boolean autoCreateTable;

    private final TransactionTemplate transactionTemplate;

    private final AtomicBoolean initialized = new AtomicBoolean();

    private final Object initLock = new Object();

    public DatabaseIdempotentStore(JdbcTemplate jdbcTemplate, String tableName, boolean autoCreateTable,
                                   PlatformTransactionManager transactionManager) {
        if (jdbcTemplate == null) {
            throw new IllegalArgumentException("JdbcTemplate 不能为空");
        }
        if (tableName == null || !TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("幂等表名不合法：" + tableName + "，只允许字母、数字、下划线与点");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.table = tableName;
        this.autoCreateTable = autoCreateTable;
        this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    }

    @Override
    public String name() {
        return "database";
    }

    @Override
    public boolean tryAcquire(String key, Duration ttl) {
        ensureReady();
        long now = System.currentTimeMillis();
        jdbcTemplate.update("DELETE FROM " + table + " WHERE idempotent_key = ? AND expire_at <= ?", key, now);
        try {
            jdbcTemplate.update("INSERT INTO " + table
                            + " (idempotent_key, state, record_payload, counter_value, expire_at) VALUES (?, ?, ?, 0, ?)",
                    key, IdempotentState.PENDING.name(), JsonUtils.toJson(IdempotentRecord.pending()),
                    expireAt(now, ttl));
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    @Override
    public void release(String key) {
        ensureReady();
        jdbcTemplate.update("DELETE FROM " + table + " WHERE idempotent_key = ?", key);
    }

    @Override
    public boolean isHeld(String key) {
        ensureReady();
        List<Integer> counts = jdbcTemplate.query(
                "SELECT COUNT(1) FROM " + table + " WHERE idempotent_key = ? AND expire_at > ?",
                (resultSet, rowNum) -> resultSet.getInt(1), key, System.currentTimeMillis());
        return !counts.isEmpty() && counts.get(0) != null && counts.get(0) > 0;
    }

    @Override
    public Optional<IdempotentRecord> findRecord(String key) {
        ensureReady();
        List<String> payloads = jdbcTemplate.query(
                "SELECT record_payload FROM " + table + " WHERE idempotent_key = ? AND expire_at > ?",
                (resultSet, rowNum) -> resultSet.getString(1), key, System.currentTimeMillis());
        if (payloads.isEmpty()) {
            return Optional.empty();
        }
        String payload = payloads.get(0);
        IdempotentRecord record = payload == null
                ? new IdempotentRecord(IdempotentState.SUCCESS, null, null, System.currentTimeMillis())
                : parseRecord(payload);
        return record == null || record.isPending() ? Optional.empty() : Optional.of(record);
    }

    @Override
    public void saveRecord(String key, IdempotentRecord record, Duration ttl) {
        ensureReady();
        String payload = JsonUtils.toJson(record);
        if (payload != null && payload.length() > MAX_PAYLOAD_LENGTH) {
            log.warn("幂等结果过大未落库，键={}，长度={}，重复提交将直接提示而不再返回首次结果", key, payload.length());
            payload = null;
        }
        upsert(key, record.state().name(), payload, expireAt(System.currentTimeMillis(), ttl));
    }

    @Override
    public long increaseFailure(String key, Duration window) {
        ensureReady();
        long now = System.currentTimeMillis();
        String failureKey = IdempotentKeys.failureKey(key);
        int updated = jdbcTemplate.update("UPDATE " + table
                + " SET counter_value = counter_value + 1 WHERE idempotent_key = ? AND expire_at > ?", failureKey, now);
        if (updated == 0) {
            jdbcTemplate.update("DELETE FROM " + table + " WHERE idempotent_key = ? AND expire_at <= ?", failureKey, now);
            try {
                jdbcTemplate.update("INSERT INTO " + table
                                + " (idempotent_key, state, record_payload, counter_value, expire_at) VALUES (?, ?, NULL, 1, ?)",
                        failureKey, IdempotentState.SUCCESS.name(), expireAt(now, window));
            } catch (DataIntegrityViolationException exception) {
                jdbcTemplate.update("UPDATE " + table
                        + " SET counter_value = counter_value + 1 WHERE idempotent_key = ?", failureKey);
            }
        }
        return failureCount(key);
    }

    @Override
    public long failureCount(String key) {
        ensureReady();
        List<Long> counters = jdbcTemplate.query(
                "SELECT counter_value FROM " + table + " WHERE idempotent_key = ? AND expire_at > ?",
                (resultSet, rowNum) -> resultSet.getLong(1), IdempotentKeys.failureKey(key),
                System.currentTimeMillis());
        return counters.isEmpty() || counters.get(0) == null ? 0L : counters.get(0);
    }

    @Override
    public void clearFailure(String key) {
        ensureReady();
        jdbcTemplate.update("DELETE FROM " + table + " WHERE idempotent_key = ?", IdempotentKeys.failureKey(key));
    }

    @Override
    public void saveToken(String token, String owner, Duration ttl) {
        ensureReady();
        upsert(IdempotentKeys.tokenKey(token), IdempotentState.TOKEN.name(),
                JsonUtils.toJson(IdempotentRecord.token(owner)), expireAt(System.currentTimeMillis(), ttl));
    }

    @Override
    public String consumeToken(String token) {
        ensureReady();
        String tokenKey = IdempotentKeys.tokenKey(token);
        long now = System.currentTimeMillis();
        if (transactionTemplate == null) {
            int deleted = jdbcTemplate.update("DELETE FROM " + table
                    + " WHERE idempotent_key = ? AND expire_at > ?", tokenKey, now);
            return deleted > 0 ? "" : null;
        }
        return transactionTemplate.execute(status -> {
            List<String> payloads = jdbcTemplate.query("SELECT record_payload FROM " + table
                            + " WHERE idempotent_key = ? AND expire_at > ? FOR UPDATE",
                    (resultSet, rowNum) -> resultSet.getString(1), tokenKey, now);
            if (payloads.isEmpty()) {
                return null;
            }
            jdbcTemplate.update("DELETE FROM " + table + " WHERE idempotent_key = ?", tokenKey);
            IdempotentRecord record = payloads.get(0) == null ? null : parseRecord(payloads.get(0));
            return record == null ? "" : record.payload();
        });
    }

    @Override
    public void clear(String key) {
        ensureReady();
        jdbcTemplate.update("DELETE FROM " + table + " WHERE idempotent_key = ?", key);
        jdbcTemplate.update("DELETE FROM " + table + " WHERE idempotent_key = ?", IdempotentKeys.failureKey(key));
    }

    private void upsert(String key, String state, String payload, long expireAt) {
        int updated = jdbcTemplate.update("UPDATE " + table
                        + " SET state = ?, record_payload = ?, expire_at = ? WHERE idempotent_key = ?",
                state, payload, expireAt, key);
        if (updated > 0) {
            return;
        }
        jdbcTemplate.update("DELETE FROM " + table + " WHERE idempotent_key = ? AND expire_at <= ?",
                key, System.currentTimeMillis());
        try {
            jdbcTemplate.update("INSERT INTO " + table
                            + " (idempotent_key, state, record_payload, counter_value, expire_at) VALUES (?, ?, ?, 0, ?)",
                    key, state, payload, expireAt);
        } catch (DataIntegrityViolationException exception) {
            jdbcTemplate.update("UPDATE " + table
                            + " SET state = ?, record_payload = ?, expire_at = ? WHERE idempotent_key = ?",
                    state, payload, expireAt, key);
        }
    }

    private void ensureReady() {
        if (!autoCreateTable || initialized.get()) {
            return;
        }
        synchronized (initLock) {
            if (initialized.get()) {
                return;
            }
            try {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + table + " ("
                        + "idempotent_key VARCHAR(255) NOT NULL PRIMARY KEY, "
                        + "state VARCHAR(16) NOT NULL, "
                        + "record_payload TEXT, "
                        + "counter_value BIGINT NOT NULL DEFAULT 0, "
                        + "expire_at BIGINT NOT NULL)");
                initialized.set(true);
            } catch (Exception exception) {
                throw new IllegalStateException("幂等表 " + table + " 初始化失败，请确认数据库可用，"
                        + "或关闭 modality.idempotent.store.auto-create-table 并手工建表", exception);
            }
        }
    }

    private static IdempotentRecord parseRecord(String payload) {
        try {
            return JsonUtils.parse(payload, IdempotentRecord.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private static long expireAt(long now, Duration ttl) {
        Duration actual = ttl == null || ttl.isZero() || ttl.isNegative() ? DEFAULT_TTL : ttl;
        return now + actual.toMillis();
    }
}
