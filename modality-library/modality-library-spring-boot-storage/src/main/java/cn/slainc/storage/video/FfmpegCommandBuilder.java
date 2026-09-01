package cn.slainc.storage.video;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FfmpegCommandBuilder {

    private final String executable;

    public FfmpegCommandBuilder(String executable) {
        this.executable = executable == null || executable.isBlank() ? "ffmpeg" : executable;
    }

    public String executable() {
        return executable;
    }

    public List<String> transcode(Path input, Path output, String videoCodec, String audioCodec, int crf) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("-y");
        command.add("-i");
        command.add(input.toString());
        command.add("-c:v");
        command.add(videoCodec == null ? "libx264" : videoCodec);
        command.add("-crf");
        command.add(String.valueOf(crf));
        command.add("-preset");
        command.add("medium");
        command.add("-c:a");
        command.add(audioCodec == null ? "aac" : audioCodec);
        command.add(output.toString());
        return command;
    }

    public List<String> compress(Path input, Path output, int crf, String preset) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("-y");
        command.add("-i");
        command.add(input.toString());
        command.add("-c:v");
        command.add("libx264");
        command.add("-crf");
        command.add(String.valueOf(crf));
        command.add("-preset");
        command.add(preset == null ? "slow" : preset);
        command.add("-c:a");
        command.add("copy");
        command.add(output.toString());
        return command;
    }

    public List<String> screenshot(Path input, Path output, double seconds) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("-y");
        command.add("-ss");
        command.add(String.format(java.util.Locale.ROOT, "%.3f", Math.max(0d, seconds)));
        command.add("-i");
        command.add(input.toString());
        command.add("-frames:v");
        command.add("1");
        command.add("-q:v");
        command.add("2");
        command.add(output.toString());
        return command;
    }
}
