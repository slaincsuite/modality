package cn.slainc.core.enums;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class StateMachine<S extends Enum<S>> {

    private final Map<S, Set<S>> transitions;
    private final S initial;

    private StateMachine(Map<S, Set<S>> transitions, S initial) {
        this.transitions = transitions;
        this.initial = initial;
    }

    public static <S extends Enum<S>> Builder<S> builder(Class<S> type) {
        return new Builder<>(type);
    }

    public boolean canTransit(S from, S to) {
        if (from == null || to == null) {
            return false;
        }
        return transitions.getOrDefault(from, EnumSet.noneOf(initial.getDeclaringClass())).contains(to);
    }

    public void checkTransit(S from, S to) {
        if (!canTransit(from, to)) {
            throw new BizException(ResultCode.DATA_STATE_INVALID,
                    "状态不允许从 " + from + " 变更为 " + to);
        }
    }

    public S initial() {
        return initial;
    }

    public Set<S> allowedTargets(S from) {
        return transitions.getOrDefault(from, Set.of());
    }

    public Map<S, Set<S>> transitions() {
        return Map.copyOf(transitions);
    }

    public static final class Builder<S extends Enum<S>> {

        private final Class<S> type;
        private final Map<S, Set<S>> collected = new LinkedHashMap<>();
        private S initial;

        private Builder(Class<S> type) {
            this.type = type;
        }

        public Builder<S> initial(S state) {
            this.initial = state;
            return this;
        }

        public Builder<S> allow(S from, S to) {
            collected.computeIfAbsent(from, key -> EnumSet.noneOf(type)).add(to);
            return this;
        }

        public Builder<S> allow(S from, S... targets) {
            for (S target : targets) {
                allow(from, target);
            }
            return this;
        }

        public StateMachine<S> build() {
            S start = initial != null ? initial : type.getEnumConstants()[0];
            return new StateMachine<>(collected, start);
        }
    }
}
