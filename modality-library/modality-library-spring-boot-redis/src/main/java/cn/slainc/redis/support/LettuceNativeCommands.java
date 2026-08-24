package cn.slainc.redis.support;

import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;

import java.util.Collections;
import java.util.List;

public final class LettuceNativeCommands {

    private static final Logger log = LoggerFactory.getLogger(LettuceNativeCommands.class);

    private LettuceNativeCommands() {
    }

    @SuppressWarnings("unchecked")
    public static RedisClusterAsyncCommands<byte[], byte[]> of(RedisConnection connection) {
        try {
            Object nativeConnection = connection.getNativeConnection();
            if (nativeConnection instanceof RedisClusterAsyncCommands<?, ?> commands) {
                return (RedisClusterAsyncCommands<byte[], byte[]>) commands;
            }
            return null;
        } catch (Throwable throwable) {
            log.warn("当前 Redis 客户端不是 Lettuce，无法执行原生命令：{}", throwable.getMessage());
            return null;
        }
    }

    public static Long memoryUsage(RedisConnection connection, byte[] key) {
        RedisClusterAsyncCommands<byte[], byte[]> commands = of(connection);
        if (commands == null) {
            return null;
        }
        try {
            return commands.memoryUsage(key).get();
        } catch (Exception exception) {
            Thread.currentThread().interrupt();
            log.warn("执行 MEMORY USAGE 失败：{}", exception.getMessage());
            return null;
        }
    }

    public static List<Object> slowlogGet(RedisConnection connection, int count) {
        RedisClusterAsyncCommands<byte[], byte[]> commands = of(connection);
        if (commands == null) {
            return Collections.emptyList();
        }
        try {
            List<Object> entries = commands.slowlogGet(count).get();
            return entries == null ? Collections.emptyList() : entries;
        } catch (Exception exception) {
            Thread.currentThread().interrupt();
            log.warn("执行 SLOWLOG GET 失败：{}", exception.getMessage());
            return Collections.emptyList();
        }
    }
}
