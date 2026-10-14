package cn.slainc.recycle.support;

import cn.slainc.core.exception.BizException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecycleSnapshotCodecTest {

    private final RecycleSnapshotCodec codec = new RecycleSnapshotCodec(JsonMapper.builder().build(), 65536);

    @Test
    void shouldRoundTripASnapshot() {
        RecycleSnapshotCodecTest.Payload payload = new RecycleSnapshotCodecTest.Payload("logo.png", 128L);

        String snapshot = codec.serialize(payload);
        RecycleSnapshotCodecTest.Payload restored = codec.deserialize(snapshot, RecycleSnapshotCodecTest.Payload.class);

        assertThat(snapshot).contains("logo.png");
        assertThat(restored).isEqualTo(payload);
    }

    @Test
    void shouldReturnNullWhenThereIsNothingToSerialize() {
        assertThat(codec.serialize(null)).isNull();
        assertThat(codec.deserialize(null, RecycleSnapshotCodecTest.Payload.class)).isNull();
        assertThat(codec.deserialize("  ", RecycleSnapshotCodecTest.Payload.class)).isNull();
        RecycleSnapshotCodecTest.Payload withoutType = codec.deserialize("{}", null);
        assertThat(withoutType).isNull();
    }

    @Test
    void shouldTruncateASnapshotThatExceedsTheByteLimit() {
        RecycleSnapshotCodec limited = new RecycleSnapshotCodec(null, 32);

        String snapshot = limited.serialize(new RecycleSnapshotCodecTest.Payload("x".repeat(200), 1L));

        assertThat(snapshot.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(32);
        assertThat(snapshot).startsWith("{\"name\":\"");
    }

    @Test
    void shouldNeverSplitAMultiByteCharacter() {
        RecycleSnapshotCodec limited = new RecycleSnapshotCodec(null, 24);

        String snapshot = limited.serialize(new RecycleSnapshotCodecTest.Payload("中文名称中文名称中文名称", 1L));

        assertThat(snapshot.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(24);
        assertThat(snapshot).doesNotContain("\uFFFD");
        assertThat(new String(snapshot.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)).isEqualTo(snapshot);
    }

    @Test
    void shouldFailWithAClearMessageWhenTheSnapshotIsBroken() {
        assertThatThrownBy(() -> codec.deserialize("{not-json", RecycleSnapshotCodecTest.Payload.class))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("回收站快照解析失败");
    }

    record Payload(String name, Long size) {
    }
}
