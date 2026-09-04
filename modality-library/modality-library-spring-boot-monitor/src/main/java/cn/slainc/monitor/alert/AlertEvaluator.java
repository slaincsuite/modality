package cn.slainc.monitor.alert;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AlertEvaluator {

    private final Map<String, RuleState> states = new ConcurrentHashMap<>();

    public List<AlertEvent> evaluate(List<AlertRule> rules, AlertMetricSource source, long nowMillis) {
        List<AlertEvent> events = new ArrayList<>();
        Set<String> active = new HashSet<>();
        if (rules == null || source == null) {
            states.keySet().retainAll(active);
            return events;
        }
        for (AlertRule rule : rules) {
            if (rule == null || rule.getId() == null || rule.getId().isBlank()) {
                continue;
            }
            active.add(rule.getId());
            if (!rule.isEnabled()) {
                continue;
            }
            Double value = source.value(rule.getMetric());
            if (value == null || value.isNaN()) {
                continue;
            }
            RuleState state = states.computeIfAbsent(rule.getId(), key -> new RuleState());
            state.lastEvaluatedAt = nowMillis;
            state.lastValue = value;
            if (rule.breached(value)) {
                if (state.breachSinceMillis == 0L) {
                    state.breachSinceMillis = nowMillis;
                }
                long heldMillis = nowMillis - state.breachSinceMillis;
                if (!state.fired && heldMillis >= rule.getDurationSeconds() * 1000L) {
                    state.fired = true;
                    events.add(AlertEvent.of(rule, AlertType.TRIGGER, value, nowMillis));
                }
            } else {
                if (state.fired) {
                    state.fired = false;
                    events.add(AlertEvent.of(rule, AlertType.RECOVER, value, nowMillis));
                }
                state.breachSinceMillis = 0L;
            }
        }
        states.keySet().retainAll(active);
        return events;
    }

    public boolean isFiring(String ruleId) {
        RuleState state = states.get(ruleId);
        return state != null && state.fired;
    }

    public long breachSinceMillis(String ruleId) {
        RuleState state = states.get(ruleId);
        return state == null ? 0L : state.breachSinceMillis;
    }

    public Double lastValue(String ruleId) {
        RuleState state = states.get(ruleId);
        return state == null ? null : state.lastValue;
    }

    public Map<String, Boolean> firingStates() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        states.forEach((ruleId, state) -> result.put(ruleId, state.fired));
        return result;
    }

    public void reset() {
        states.clear();
    }

    private static final class RuleState {

        private long breachSinceMillis;

        private boolean fired;

        private double lastValue;

        private long lastEvaluatedAt;
    }
}
