package cn.slainc.workorder.engine;

import java.util.List;
import java.util.Map;


public record BatchTransitionResult(List<Long> succeeded, Map<Long, String> failures) {

    public boolean isFullySucceeded() {
        return failures == null || failures.isEmpty();
    }
}
