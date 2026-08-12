package cn.slainc.utils.string;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TemplateUtils {

    private static final Pattern PLACEHOLDER = Pattern.compile("[$#]\\{([^{}]*)}");

    private TemplateUtils() {
    }

    public static String render(String template, Map<String, ?> variables) {
        return render(template, variables, true);
    }

    public static String render(String template, Map<String, ?> variables, boolean keepUnresolved) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = variables == null ? null : variables.get(key);
            if (value == null && variables != null && variables.containsKey(key)) {
                value = "";
            }
            String replacement;
            if (value != null) {
                replacement = String.valueOf(value);
            } else {
                replacement = keepUnresolved ? matcher.group(0) : "";
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    public static Set<String> extractKeys(String template) {
        Set<String> keys = new LinkedHashSet<>();
        if (template == null || template.isEmpty()) {
            return keys;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    public static String renderValues(String template, Map<String, ?> variables) {
        return render(template, variables, false);
    }
}
