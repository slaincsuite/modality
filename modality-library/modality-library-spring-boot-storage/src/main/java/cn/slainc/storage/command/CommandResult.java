package cn.slainc.storage.command;

import java.util.List;

public record CommandResult(List<String> command, int exitCode, String stdout, String stderr, boolean timedOut,
                            boolean executableMissing, long durationMillis) {

    public boolean success() {
        return !timedOut && !executableMissing && exitCode == 0;
    }

    public String message() {
        if (executableMissing) {
            return "外部命令不存在或不可执行：" + (command.isEmpty() ? "" : command.get(0));
        }
        if (timedOut) {
            return "外部命令执行超时：" + String.join(" ", command);
        }
        if (exitCode != 0) {
            return "外部命令执行失败（退出码 " + exitCode + "）：" + String.join(" ", command)
                    + (stderr.isBlank() ? "" : "，错误输出：" + stderr.strip());
        }
        return "外部命令执行成功：" + String.join(" ", command);
    }

    public String commandLine() {
        return String.join(" ", command);
    }
}
