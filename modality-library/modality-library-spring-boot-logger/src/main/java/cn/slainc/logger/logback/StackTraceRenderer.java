package cn.slainc.logger.logback;

import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;

import java.util.ArrayList;
import java.util.List;

public final class StackTraceRenderer {

    private static final int MAX_CAUSE_DEPTH = 16;

    public static List<String> render(IThrowableProxy proxy, int maxDepth) {
        if (proxy == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        IThrowableProxy current = proxy;
        int depth = 0;
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            StringBuilder header = new StringBuilder();
            if (depth > 0) {
                header.append("Caused by: ");
            }
            header.append(current.getClassName());
            if (current.getMessage() != null) {
                header.append(": ").append(current.getMessage());
            }
            lines.add(header.toString());
            StackTraceElementProxy[] elements = current.getStackTraceElementProxyArray();
            int commonFrames = current.getCommonFrames();
            if (elements != null) {
                int limit = elements.length - Math.max(0, commonFrames);
                for (int i = 0; i < limit; i++) {
                    lines.add("\t" + frameText(elements[i]));
                }
                if (commonFrames > 0) {
                    lines.add("\t... " + commonFrames + " common frames omitted");
                }
            }
            current = current.getCause();
            depth++;
        }
        if (lines.size() <= maxDepth || maxDepth <= 0) {
            return lines;
        }
        List<String> truncated = new ArrayList<>(lines.subList(0, maxDepth));
        truncated.add("...(堆栈共 " + lines.size() + " 行，已截断)");
        return truncated;
    }

    public static String rootCauseClassName(IThrowableProxy proxy) {
        IThrowableProxy current = proxy;
        int depth = 0;
        while (current.getCause() != null && depth < MAX_CAUSE_DEPTH) {
            current = current.getCause();
            depth++;
        }
        return current.getClassName();
    }

    private static String frameText(StackTraceElementProxy proxy) {
        String text = proxy.getSTEAsString();
        if (text == null) {
            StackTraceElement element = proxy.getStackTraceElement();
            text = element == null ? "未知调用栈" : element.toString();
        }
        return text.startsWith("at ") ? text : "at " + text;
    }

    private StackTraceRenderer() {
    }
}
