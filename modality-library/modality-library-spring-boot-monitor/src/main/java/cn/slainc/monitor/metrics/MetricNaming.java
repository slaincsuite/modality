package cn.slainc.monitor.metrics;

public class MetricNaming {

    public static final String DEFAULT_PREFIX = "modality";

    private final String prefix;

    public MetricNaming() {
        this(DEFAULT_PREFIX);
    }

    public MetricNaming(String prefix) {
        this.prefix = normalizePrefix(prefix);
    }

    public String prefix() {
        return prefix;
    }

    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return prefix;
        }
        StringBuilder builder = new StringBuilder(raw.length() + 16);
        char previous = 0;
        for (int index = 0; index < raw.length(); index++) {
            char current = raw.charAt(index);
            if (Character.isUpperCase(current) && Character.isLowerCase(previous)) {
                builder.append('.');
            }
            if (Character.isLetterOrDigit(current)) {
                builder.append(Character.toLowerCase(current));
            } else {
                builder.append('.');
            }
            previous = current;
        }
        String name = collapse(builder.toString());
        if (name.isEmpty()) {
            return prefix;
        }
        if (name.equals(prefix) || name.startsWith(prefix + ".")) {
            return name;
        }
        return prefix + "." + name;
    }

    public boolean isCanonical(String name) {
        return name != null && name.equals(normalize(name));
    }

    public static String toPrometheusName(String name) {
        return name == null ? null : name.replace('.', '_').replace('-', '_');
    }

    private static String collapse(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean pendingDot = true;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '.') {
                if (!pendingDot) {
                    builder.append('.');
                    pendingDot = true;
                }
                continue;
            }
            builder.append(current);
            pendingDot = false;
        }
        if (!builder.isEmpty() && builder.charAt(builder.length() - 1) == '.') {
            builder.setLength(builder.length() - 1);
        }
        return builder.toString();
    }

    private static String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PREFIX;
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isLetterOrDigit(current)) {
                builder.append(Character.toLowerCase(current));
            }
        }
        return builder.isEmpty() ? DEFAULT_PREFIX : builder.toString();
    }
}
