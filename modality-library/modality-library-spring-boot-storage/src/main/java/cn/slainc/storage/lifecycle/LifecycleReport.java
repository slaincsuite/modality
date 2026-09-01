package cn.slainc.storage.lifecycle;

import java.util.List;

public record LifecycleReport(int scanned, int expired, int archived, int failed, long reclaimedBytes,
                              List<String> failures) {

    public static LifecycleReport empty() {
        return new LifecycleReport(0, 0, 0, 0, 0L, List.of());
    }
}
