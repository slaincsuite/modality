package cn.slainc.redis.serialize;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CompressedRedisSerializer<T> implements RedisSerializer<T> {

    public static final byte MAGIC_RAW = 0x00;

    public static final byte MAGIC_GZIP = 0x01;

    private static final int DEFAULT_THRESHOLD = 4096;

    private final RedisSerializer<T> delegate;

    private final int threshold;

    public CompressedRedisSerializer(RedisSerializer<T> delegate) {
        this(delegate, DEFAULT_THRESHOLD);
    }

    public CompressedRedisSerializer(RedisSerializer<T> delegate, int threshold) {
        if (delegate == null) {
            throw new IllegalArgumentException("被包装的序列化器不能为空");
        }
        this.delegate = delegate;
        this.threshold = Math.max(threshold, 1);
    }

    public RedisSerializer<T> getDelegate() {
        return delegate;
    }

    public int getThreshold() {
        return threshold;
    }

    @Override
    public byte[] serialize(T value) throws SerializationException {
        if (value == null) {
            return null;
        }
        byte[] payload = delegate.serialize(value);
        if (payload == null || payload.length == 0) {
            return payload;
        }
        if (payload.length < threshold) {
            return withMagic(payload, MAGIC_RAW);
        }
        byte[] compressed = gzip(payload);
        if (compressed.length >= payload.length) {
            return withMagic(payload, MAGIC_RAW);
        }
        return withMagic(compressed, MAGIC_GZIP);
    }

    @Override
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        byte magic = bytes[0];
        byte[] payload = new byte[bytes.length - 1];
        System.arraycopy(bytes, 1, payload, 0, payload.length);
        if (magic == MAGIC_GZIP) {
            return delegate.deserialize(gunzip(payload));
        }
        if (magic == MAGIC_RAW) {
            return delegate.deserialize(payload);
        }
        // 兼容未压缩格式的历史数据：整段都当作载荷
        return delegate.deserialize(bytes);
    }

    private byte[] withMagic(byte[] payload, byte magic) {
        byte[] result = new byte[payload.length + 1];
        result[0] = magic;
        System.arraycopy(payload, 0, result, 1, payload.length);
        return result;
    }

    private byte[] gzip(byte[] payload) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(payload.length);
        try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
            gzip.write(payload);
        } catch (Exception ex) {
            throw new SerializationException("Redis 值压缩失败：" + ex.getMessage(), ex);
        }
        return buffer.toByteArray();
    }

    private byte[] gunzip(byte[] payload) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(payload))) {
            return gzip.readAllBytes();
        } catch (Exception ex) {
            throw new SerializationException("Redis 值解压失败：" + ex.getMessage(), ex);
        }
    }
}
