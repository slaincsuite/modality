package cn.slainc.redis.stream;

import cn.slainc.redis.autoconfigure.RedisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.ByteRecord;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RedisStreamTemplate {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamTemplate.class);

    private static final String PAYLOAD_FIELD = "payload";

    private static final String RETRY_FIELD = "retryCount";

    private final StringRedisTemplate stringRedisTemplate;

    private final RedisProperties properties;

    public RedisStreamTemplate(StringRedisTemplate stringRedisTemplate, RedisProperties properties) {
        if (stringRedisTemplate == null) {
            throw new IllegalArgumentException("stringRedisTemplate 不能为空");
        }
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    public int getBatchSize() {
        return Math.max(properties.getStream().getBatchSize(), 1);
    }

    public String produce(String stream, String payload) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put(PAYLOAD_FIELD, payload);
        return produce(stream, body);
    }

    public String produce(String stream, Map<String, String> body) {
        requireStream(stream);
        Map<byte[], byte[]> raw = new LinkedHashMap<>();
        if (body != null) {
            body.forEach((key, value) -> raw.put(bytes(key), bytes(value == null ? "" : value)));
        }
        MapRecord<byte[], byte[], byte[]> record = MapRecord.create(bytes(stream), raw);
        RecordId recordId = stringRedisTemplate.execute((RedisCallback<RecordId>) connection ->
                connection.streamCommands().xAdd(record, RedisStreamCommands.XAddOptions.none()));
        return recordId == null ? null : recordId.getValue();
    }

    public String createGroup(String stream, String group) {
        requireStream(stream);
        return stringRedisTemplate.execute((RedisCallback<String>) connection ->
                connection.streamCommands().xGroupCreate(bytes(stream), group, ReadOffset.from("0-0"), true));
    }

    public boolean ensureGroup(String stream, String group) {
        try {
            createGroup(stream, group);
            return true;
        } catch (Exception exception) {
            if (containsBusyGroup(exception)) {
                return false;
            }
            throw exception;
        }
    }

    public List<StreamRecord> consume(String stream, String group, String consumer) {
        return consume(stream, group, consumer, properties.getStream().getBatchSize(), null);
    }

    public List<StreamRecord> consume(String stream, String group, String consumer, int batchSize) {
        return consume(stream, group, consumer, batchSize, null);
    }

    public List<StreamRecord> consume(String stream, String group, String consumer, int batchSize, Duration block) {
        requireStream(stream);
        ensureGroup(stream, group);
        StreamReadOptions options = block == null
                ? StreamReadOptions.empty().count(Math.max(batchSize, 1))
                : StreamReadOptions.empty().count(Math.max(batchSize, 1)).block(block);
        List<ByteRecord> records = stringRedisTemplate.execute((RedisCallback<List<ByteRecord>>) connection ->
                connection.streamCommands().xReadGroup(Consumer.from(group, consumer), options,
                        StreamOffset.create(bytes(stream), ReadOffset.lastConsumed())));
        List<StreamRecord> result = new ArrayList<>();
        if (records == null) {
            return result;
        }
        for (ByteRecord record : records) {
            StreamRecord item = new StreamRecord(stream, group, record.getId().getValue());
            record.getValue().forEach((key, value) -> item.getBody()
                    .put(new String(key, StandardCharsets.UTF_8), new String(value, StandardCharsets.UTF_8)));
            result.add(item);
        }
        return result;
    }

    public long ack(String stream, String group, String... ids) {
        requireStream(stream);
        Long acked = stringRedisTemplate.execute((RedisCallback<Long>) connection ->
                connection.streamCommands().xAck(bytes(stream), group, ids));
        return acked == null ? 0L : acked;
    }

    public List<StreamRecord> pending(String stream, String group) {
        return pending(stream, group, Math.max(properties.getStream().getBatchSize(), 1));
    }

    public List<StreamRecord> pending(String stream, String group, int count) {
        requireStream(stream);
        ensureGroup(stream, group);
        PendingMessages messages = stringRedisTemplate.execute((RedisCallback<PendingMessages>) connection ->
                connection.streamCommands().xPending(bytes(stream), group, Range.unbounded(), (long) Math.max(count, 1)));
        List<StreamRecord> result = new ArrayList<>();
        if (messages == null) {
            return result;
        }
        for (PendingMessage message : messages) {
            StreamRecord item = new StreamRecord(stream, group, message.getIdAsString());
            item.setDeliveryCount(message.getTotalDeliveryCount());
            result.add(item);
        }
        return result;
    }

    public StreamRetryResult retry(String stream, String group, StreamRecord record) {
        if (record == null || record.getId() == null) {
            return StreamRetryResult.MISSING;
        }
        long current = record.getBody().containsKey(RETRY_FIELD)
                ? parseLong(record.getBody().get(RETRY_FIELD))
                : record.getDeliveryCount();
        long nextDelivery = current + 1L;
        boolean deadLetter = nextDelivery >= Math.max(properties.getStream().getMaxRetry(), 1);
        Map<String, String> body = new LinkedHashMap<>(record.getBody());
        body.put(RETRY_FIELD, String.valueOf(nextDelivery));
        if (deadLetter) {
            body.put("死信原因", "超过最大重试次数 " + properties.getStream().getMaxRetry());
            produce(deadLetterStream(stream), body);
        } else {
            produce(stream, body);
        }
        ack(stream, group, record.getId());
        log.info("流消息重试处理，流={}，编号={}，投递次数={}，结果={}", stream, record.getId(), nextDelivery,
                deadLetter ? StreamRetryResult.DEAD_LETTERED.getDescription() : StreamRetryResult.RETRIED.getDescription());
        return deadLetter ? StreamRetryResult.DEAD_LETTERED : StreamRetryResult.RETRIED;
    }

    public String deadLetterStream(String stream) {
        return stream + properties.getStream().getDeadLetterSuffix();
    }

    public long size(String stream) {
        Long size = stringRedisTemplate.execute((RedisCallback<Long>) connection ->
                connection.streamCommands().xLen(bytes(stream)));
        return size == null ? 0L : size;
    }

    public long trim(String stream, long maxLength) {
        Long trimmed = stringRedisTemplate.opsForStream().trim(stream, maxLength);
        return trimmed == null ? 0L : trimmed;
    }

    public long delete(String stream, String... ids) {
        Long deleted = stringRedisTemplate.execute((RedisCallback<Long>) connection ->
                connection.streamCommands().xDel(bytes(stream), ids));
        return deleted == null ? 0L : deleted;
    }

    public boolean deleteGroup(String stream, String group) {
        Boolean deleted = stringRedisTemplate.execute((RedisCallback<Boolean>) connection ->
                connection.streamCommands().xGroupDestroy(bytes(stream), group));
        return Boolean.TRUE.equals(deleted);
    }

    private static boolean containsBusyGroup(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (String.valueOf(current.getMessage()).contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static long parseLong(String text) {
        try {
            return Long.parseLong(String.valueOf(text));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private void requireStream(String stream) {
        if (stream == null || stream.isBlank()) {
            throw new IllegalArgumentException("Redis 流的名称不能为空");
        }
    }

    private static byte[] bytes(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }
}
