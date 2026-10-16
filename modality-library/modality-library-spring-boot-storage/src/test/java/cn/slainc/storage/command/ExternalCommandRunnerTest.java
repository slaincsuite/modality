package cn.slainc.storage.command;

import cn.slainc.storage.spi.VideoMetadata;
import cn.slainc.storage.video.FfmpegCommandBuilder;
import cn.slainc.storage.video.FfprobeCommandBuilder;
import cn.slainc.storage.video.VideoMetadataReader;
import cn.slainc.storage.video.VideoProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalCommandRunnerTest {

    @TempDir
    Path directory;

    @Test
    @DisplayName("外部工具缺失时不抛异常，返回可识别的降级结果并给出中文提示")
    void missingExecutableDegradesGracefully() {
        ExternalCommandRunner runner = new ExternalCommandRunner(Duration.ofSeconds(5));
        CommandResult result = runner.run(List.of("definitely-not-a-real-tool-xyz", "--version"));
        assertThat(result.executableMissing()).isTrue();
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("外部命令不存在或不可执行");
        assertThat(runner.available("definitely-not-a-real-tool-xyz")).isFalse();
    }

    @Test
    @DisplayName("用假 ffmpeg 脚本验证命令构造与调用，转码/截图成功")
    void fakeFfmpegIsInvokedWithExpectedArguments() throws IOException {
        Path log = directory.resolve("ffmpeg-args.log");
        Path fake = ffmpegStub(log);
        Path input = Files.writeString(directory.resolve("in.mp4"), "fake video");
        Path output = directory.resolve("out.jpg");

        VideoProcessor processor = new VideoProcessor(new FfmpegCommandBuilder(fake.toString()),
                new ExternalCommandRunner(Duration.ofSeconds(20)), Duration.ofSeconds(20));
        assertThat(processor.available()).isTrue();

        CommandResult screenshot = processor.screenshot(input, output, 3.5d);
        assertThat(screenshot.success()).isTrue();
        assertThat(Files.exists(output)).isTrue();

        String arguments = Files.readString(log, StandardCharsets.UTF_8);
        assertThat(arguments).contains("-ss 3.500").contains("-i " + input).contains("-frames:v 1")
                .contains(output.toString());

        CommandResult transcode = processor.transcode(input, directory.resolve("out.mp4"), "libx264", "aac", 23);
        assertThat(transcode.success()).isTrue();
        assertThat(Files.readString(log, StandardCharsets.UTF_8)).contains("-c:v libx264").contains("-crf 23");
    }

    @Test
    @DisplayName("ffmpeg 不存在时视频处理返回失败而不是抛未捕获异常")
    void ffmpegMissingReturnsFailure() throws IOException {
        Path input = Files.writeString(directory.resolve("in.mp4"), "fake video");
        VideoProcessor processor = new VideoProcessor(new FfmpegCommandBuilder("no-such-ffmpeg-xyz"),
                new ExternalCommandRunner(Duration.ofSeconds(5)), Duration.ofSeconds(5));
        assertThat(processor.available()).isFalse();
        CommandResult result = processor.screenshot(input, directory.resolve("out.jpg"), 0d);
        assertThat(result.executableMissing()).isTrue();
        assertThat(result.success()).isFalse();
    }

    @Test
    @DisplayName("ffprobe 缺失时视频元数据降级为 null，不抛异常")
    void ffprobeMissingReturnsNull() throws IOException {
        Path input = Files.writeString(directory.resolve("in.mp4"), "fake video");
        VideoMetadataReader reader = new VideoMetadataReader(new FfprobeCommandBuilder("no-such-ffprobe-xyz"),
                new ExternalCommandRunner(Duration.ofSeconds(5)));
        assertThat(reader.read(input)).isNull();
    }

    @Test
    @DisplayName("ffprobe 返回 JSON 时正确解析时长、分辨率与编码")
    void ffprobeJsonIsParsed() throws IOException {
        String json = "{\"streams\":[{\"codec_type\":\"audio\",\"codec_name\":\"aac\"},"
                + "{\"codec_type\":\"video\",\"codec_name\":\"h264\",\"width\":1920,\"height\":1080}],"
                + "\"format\":{\"duration\":\"12.500000\",\"bit_rate\":\"1500000\",\"format_name\":\"mov,mp4\"}}";
        Path fake = ffprobeStub(directory.resolve("probe.log"), json);
        Path input = Files.writeString(directory.resolve("in.mp4"), "fake video");

        VideoMetadataReader reader = new VideoMetadataReader(new FfprobeCommandBuilder(fake.toString()),
                new ExternalCommandRunner(Duration.ofSeconds(20)));
        VideoMetadata metadata = reader.read(input);
        assertThat(metadata).isNotNull();
        assertThat(metadata.durationSeconds()).isEqualTo(12.5d);
        assertThat(metadata.width()).isEqualTo(1920);
        assertThat(metadata.height()).isEqualTo(1080);
        assertThat(metadata.codec()).isEqualTo("h264");
        assertThat(metadata.bitRate()).isEqualTo(1500000L);
    }

    @Test
    @DisplayName("命令执行超时会被强制终止并标记超时")
    void timeoutIsReported() throws IOException {
        Path fake = slowStub(directory.resolve("slow.log"));
        ExternalCommandRunner runner = new ExternalCommandRunner(Duration.ofSeconds(1));
        long start = System.currentTimeMillis();
        CommandResult result = runner.run(List.of(fake.toString()), Duration.ofSeconds(1));
        assertThat(result.timedOut()).isTrue();
        assertThat(result.success()).isFalse();
        assertThat(System.currentTimeMillis() - start).isLessThan(20_000L);
    }

    // Windows 没有 /bin/sh 与 POSIX 权限：按平台生成 .cmd 或 .sh 假工具，断言口径保持一致
    private Path ffmpegStub(Path log) throws IOException {
        if (isWindows()) {
            return windowsStub("fake-ffmpeg.cmd", log,
                    List.of("for %%a in (%*) do set \"out=%%~a\"", ">\"%out%\" echo fake"));
        }
        return unixStub("fake-ffmpeg.sh", log,
                List.of("for last in \"$@\"; do out=\"$last\"; done", "echo fake > \"$out\""));
    }

    private Path ffprobeStub(Path log, String json) throws IOException {
        Path payload = Files.writeString(directory.resolve("ffprobe-payload.json"), json);
        if (isWindows()) {
            return windowsStub("fake-ffprobe.cmd", log, List.of("type \"" + payload + "\""));
        }
        return unixStub("fake-ffprobe.sh", log, List.of("cat \"" + payload + "\""));
    }

    private Path slowStub(Path log) throws IOException {
        if (isWindows()) {
            return windowsStub("slow.cmd", log, List.of("ping -n 31 127.0.0.1 > nul"));
        }
        return unixStub("slow.sh", log, List.of("sleep 30"));
    }

    private Path windowsStub(String name, Path log, List<String> body) throws IOException {
        Path script = directory.resolve(name);
        StringBuilder content = new StringBuilder("@echo off\r\n");
        content.append(">>\"").append(log).append("\" echo %*\r\n");
        body.forEach(line -> content.append(line).append("\r\n"));
        return Files.writeString(script, content.toString());
    }

    private Path unixStub(String name, Path log, List<String> body) throws IOException {
        Path script = directory.resolve(name);
        StringBuilder content = new StringBuilder("#!/bin/sh\n");
        content.append("echo \"$@\" >> ").append(log).append("\n");
        body.forEach(line -> content.append(line).append("\n"));
        Files.writeString(script, content.toString());
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
