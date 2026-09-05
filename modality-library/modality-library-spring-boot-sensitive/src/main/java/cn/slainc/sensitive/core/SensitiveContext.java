package cn.slainc.sensitive.core;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

public final class SensitiveContext {

    private static final ThreadLocal<Boolean> ENABLED = new ThreadLocal<>();

    private static final ThreadLocal<Set<String>> GROUPS = new ThreadLocal<>();

    private SensitiveContext() {
    }

    public static boolean isEnabled() {
        Boolean enabled = ENABLED.get();
        return enabled == null || enabled;
    }

    public static void setEnabled(boolean enabled) {
        ENABLED.set(enabled);
    }

    public static void enable() {
        ENABLED.set(true);
    }

    public static void disable() {
        ENABLED.set(false);
    }

    public static Set<String> groups() {
        Set<String> groups = GROUPS.get();
        return groups == null ? Set.of() : groups;
    }

    public static void setGroups(String... groups) {
        setGroups(groups == null ? Set.of() : new LinkedHashSet<>(Arrays.asList(groups)));
    }

    public static void setGroups(Collection<String> groups) {
        if (groups == null || groups.isEmpty()) {
            GROUPS.remove();
            return;
        }
        GROUPS.set(Set.copyOf(groups));
    }

    public static boolean matches(Collection<String> required) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        Set<String> active = GROUPS.get();
        if (active == null || active.isEmpty()) {
            return false;
        }
        for (String group : required) {
            if (active.contains(group)) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        ENABLED.remove();
        GROUPS.remove();
    }

    public static void runWith(boolean enabled, Collection<String> groups, Runnable action) {
        callWith(enabled, groups, () -> {
            action.run();
            return null;
        });
    }

    public static <T> T callWith(boolean enabled, Collection<String> groups, Supplier<T> action) {
        Boolean previousEnabled = ENABLED.get();
        Set<String> previousGroups = GROUPS.get();
        try {
            setEnabled(enabled);
            setGroups(groups);
            return action.get();
        } finally {
            if (previousEnabled == null) {
                ENABLED.remove();
            } else {
                ENABLED.set(previousEnabled);
            }
            if (previousGroups == null) {
                GROUPS.remove();
            } else {
                GROUPS.set(previousGroups);
            }
        }
    }

    public static void runWithoutSensitive(Runnable action) {
        runWith(false, null, action);
    }

    public static <T> T callWithoutSensitive(Supplier<T> action) {
        return callWith(false, null, action);
    }
}
