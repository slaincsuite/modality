package cn.slainc.modules.infra.store;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisChunkSessionStoreTest {

    @Test
    void roundTripsSessionThroughRedisTemplate() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        Map<String, String> redis = new HashMap<>();
        when(template.opsForValue()).thenReturn(values);
        doAnswer(invocation -> {
            redis.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.get(anyString())).thenAnswer(invocation -> redis.get(invocation.getArgument(0)));
        doAnswer(invocation -> redis.remove(invocation.getArgument(0)) != null).when(template).delete(anyString());

        RedisChunkSessionStore store = new RedisChunkSessionStore(template, Duration.ofMinutes(30));
        ChunkSession session = new ChunkSession("upload-1", "tenant/1/归档 文件.bin", "归档 文件.bin",
                "application/octet-stream", "批次 12|归档", 5 * 1024 * 1024, 6 * 1024 * 1024, "94001");

        store.save(session);

        assertThat(store.find("upload-1")).isEqualTo(session);
        assertThat(RedisChunkSessionStore.redisKey("upload-1")).isEqualTo("modality:infra:chunk:upload-1");

        store.remove("upload-1");
        assertThat(store.find("upload-1")).isNull();
        assertThat(store.find("missing")).isNull();
    }
}
