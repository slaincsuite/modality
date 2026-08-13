package cn.slainc.core.exception;

import java.util.ArrayList;
import java.util.List;

public final class ExceptionUtils {

    private static final String[] DEFAULT_TRIM_PREFIXES = {
            "org.springframework.",
            "java.base/",
            "jakarta.servlet.",
            "org.apache.catalina.",
            "org.apache.tomcat.",
            "jdk.internal.reflect.",
            "java.lang.reflect."
    };

    private ExceptionUtils() {
    }

    public static Throwable getRootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    public static List<Throwable> getCauseChain(Throwable throwable) {
        List<Throwable> chain = new ArrayList<>();
        Throwable current = throwable;
        while (current != null && !chain.contains(current)) {
            chain.add(current);
            current = current.getCause();
        }
        return chain;
    }

    public static String getMessage(Throwable throwable) {
        Throwable root = getRootCause(throwable);
        return root == null ? null : root.getMessage();
    }

    public static Throwable trim(Throwable throwable) {
        return trim(throwable, DEFAULT_TRIM_PREFIXES);
    }

    public static Throwable trim(Throwable throwable, String... packagePrefixes) {
        if (throwable == null) {
            return null;
        }
        for (Throwable current : getCauseChain(throwable)) {
            StackTraceElement[] elements = current.getStackTrace();
            if (elements == null || elements.length == 0) {
                continue;
            }
            List<StackTraceElement> kept = new ArrayList<>(elements.length);
            for (StackTraceElement element : elements) {
                if (!matches(element.getClassName(), packagePrefixes)) {
                    kept.add(element);
                }
            }
            current.setStackTrace(kept.toArray(new StackTraceElement[0]));
        }
        return throwable;
    }

    public static String buildMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (Throwable current : getCauseChain(throwable)) {
            if (builder.length() > 0) {
                builder.append(" <- ");
            }
            builder.append(current.getClass().getSimpleName());
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                builder.append(": ").append(message);
            }
        }
        return builder.toString();
    }

    private static boolean matches(String className, String[] prefixes) {
        if (className == null || prefixes == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (prefix != null && className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
