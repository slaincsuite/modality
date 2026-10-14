package cn.slainc.storage.video;

import cn.slainc.storage.command.CommandResult;
import cn.slainc.storage.command.ExternalCommandRunner;
import cn.slainc.storage.spi.VideoMetadata;
import cn.slainc.utils.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class VideoMetadataReader {

    private static final Logger log = LoggerFactory.getLogger(VideoMetadataReader.class);

    private final FfprobeCommandBuilder builder;

    private final ExternalCommandRunner runner;

    public VideoMetadataReader(FfprobeCommandBuilder builder, ExternalCommandRunner runner) {
        this.builder = builder;
        this.runner = runner;
    }

    public VideoMetadata read(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            log.warn("视频文件不存在，无法读取元数据：{}", file);
            return null;
        }
        CommandResult result = runner.run(builder.probe(file), Duration.ofSeconds(20));
        if (result.executableMissing()) {
            log.warn("未检测到 ffprobe（{}），视频元数据读取已降级为返回空值；如需视频时长与分辨率请安装 ffmpeg",
                    builder.executable());
            return null;
        }
        if (!result.success()) {
            log.warn("ffprobe 读取视频元数据失败，已降级为返回空值：{}", result.message());
            return null;
        }
        try {
            JsonNode root = JsonUtils.readTree(result.stdout());
            JsonNode video = null;
            for (JsonNode stream : root.path("streams")) {
                if ("video".equals(stream.path("codec_type").asString())) {
                    video = stream;
                    break;
                }
            }
            if (video == null) {
                log.warn("ffprobe 输出中没有视频流：{}", file);
                return null;
            }
            JsonNode format = root.path("format");
            double duration = parseDouble(format.path("duration").asString(), 0d);
            long bitRate = (long) parseDouble(format.path("bit_rate").asString(), 0d);
            return new VideoMetadata(duration, video.path("width").asInt(0), video.path("height").asInt(0),
                    video.path("codec_name").asString(), format.path("format_name").asString(), bitRate);
        } catch (RuntimeException ex) {
            log.warn("解析 ffprobe 输出失败，已降级为返回空值：{}", ex.getMessage());
            return null;
        }
    }

    private static double parseDouble(String text, double defaultValue) {
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
