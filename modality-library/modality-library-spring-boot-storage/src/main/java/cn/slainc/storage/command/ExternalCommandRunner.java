package cn.slainc.storage.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ExternalCommandRunner {

    private static final Logger log = LoggerFactory.getLogger(ExternalCommandRunner.class);

    private final Duration defaultTimeout;

    public ExternalCommandRunner() {
        this(Duration.ofSeconds(30));
    }

    public ExternalCommandRunner(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }

    public CommandResult run(List<String> command) {
        return run(command, defaultTimeout);
    }

    public CommandResult run(List<String> command, Duration timeout) {
        long start = System.currentTimeMillis();
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);
        Process process;
        try {
            process = builder.start();
        } catch (IOException ex) {
            log.warn("外部命令不可用，已降级：{}，原因：{}", String.join(" ", command), ex.getMessage());
            return new CommandResult(List.copyOf(command), -1, "", ex.getMessage() == null ? "" : ex.getMessage(),
                    false, true, System.currentTimeMillis() - start);
        }
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread outReader = Thread.ofVirtual().start(() -> read(process.getInputStream(), stdout));
        Thread errReader = Thread.ofVirtual().start(() -> read(process.getErrorStream(), stderr));
        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            outReader.join(2000);
            errReader.join(2000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new CommandResult(List.copyOf(command), -1, stdout.toString(), stderr.toString(), true, false,
                    System.currentTimeMillis() - start);
        }
        return new CommandResult(List.copyOf(command), finished ? process.exitValue() : -1, stdout.toString(),
                stderr.toString(), !finished, false, System.currentTimeMillis() - start);
    }

    public boolean available(String executable) {
        Path resolved = which(executable);
        return resolved != null && Files.isExecutable(resolved);
    }

    public static Path which(String executable) {
        if (executable == null || executable.isBlank()) {
            return null;
        }
        Path direct = Path.of(executable);
        if (direct.isAbsolute()) {
            return Files.isExecutable(direct) ? direct : null;
        }
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String directory : path.split(java.io.File.pathSeparator)) {
            if (directory.isBlank()) {
                continue;
            }
            Path candidate = Path.of(directory).resolve(executable);
            if (Files.isExecutable(candidate) && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static void read(InputStream input, StringBuilder target) {
        try (InputStream stream = input) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                target.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
        } catch (IOException ex) {
            log.debug("读取外部命令输出失败：{}", ex.getMessage());
        }
    }
}
