package cn.slainc.storage.video;

import java.nio.file.Path;
import java.util.List;

public final class FfprobeCommandBuilder {

    private final String executable;

    public FfprobeCommandBuilder(String executable) {
        this.executable = executable == null || executable.isBlank() ? "ffprobe" : executable;
    }

    public List<String> probe(Path input) {
        return List.of(executable, "-v", "quiet", "-print_format", "json", "-show_format", "-show_streams",
                input.toString());
    }

    public String executable() {
        return executable;
    }
}
