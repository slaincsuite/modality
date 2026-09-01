package cn.slainc.storage.migration;

import java.util.List;

public record MigrationReport(String from, String to, int total, int succeeded, int skipped, int failed, long bytes,
                              List<String> failures) {
}
