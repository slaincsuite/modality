package cn.slainc.storage.video;

import cn.slainc.storage.command.CommandResult;
import cn.slainc.storage.command.ExternalCommandRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class VideoProcessor {

    private static final Logger log = LoggerFactory.getLogger(VideoProcessor.class);

    private final FfmpegCommandBuilder builder;

    private final ExternalCommandRunner runner;

    private final Duration timeout;

    public VideoProcessor(FfmpegCommandBuilder builder, ExternalCommandRunner runner, Duration timeout) {
        this.builder = builder;
        this.runner = runner;
        this.timeout = timeout == null ? Duration.ofMinutes(10) : timeout;
    }

    public boolean available() {
        return runner.available(builder.executable());
    }

    public CommandResult transcode(Path input, Path output, String videoCodec, String audioCodec, int crf) {
        return execute(builder.transcode(input, output, videoCodec, audioCodec, crf), output);
    }

    public CommandResult compress(Path input, Path output, int crf, String preset) {
        return execute(builder.compress(input, output, crf, preset), output);
    }

    public CommandResult screenshot(Path input, Path output, double seconds) {
        return execute(builder.screenshot(input, output, seconds), output);
    }

    public CommandResult firstFrame(Path input, Path output) {
        return screenshot(input, output, 0d);
    }

    private CommandResult execute(java.util.List<String> command, Path output) {
        if (!runner.available(command.get(0))) {
            log.warn("未检测到 FFmpeg（{}），视频处理已降级为失败返回；如需视频转码/截图请安装 ffmpeg",
                    command.get(0));
            return new CommandResult(java.util.List.copyOf(command), -1, "", "ffmpeg 不可用", false, true, 0L);
        }
        CommandResult result = runner.run(command, timeout);
        if (result.success() && output != null && !Files.isRegularFile(output)) {
            log.warn("FFmpeg 返回成功但未生成目标文件：{}", output);
            return new CommandResult(result.command(), -1, result.stdout(), result.stderr(), false, false,
                    result.durationMillis());
        }
        if (!result.success()) {
            log.warn("视频处理失败：{}", result.message());
        }
        return result;
    }
}
