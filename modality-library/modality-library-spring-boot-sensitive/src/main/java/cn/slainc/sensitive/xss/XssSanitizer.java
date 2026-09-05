package cn.slainc.sensitive.xss;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

public class XssSanitizer {

    private static final PolicyFactory PLAIN_TEXT = new HtmlPolicyBuilder().toFactory();

    private static final PolicyFactory RICH_TEXT = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.TABLES);

    public String clean(String html) {
        return html == null ? null : PLAIN_TEXT.sanitize(html);
    }

    public String cleanRich(String html) {
        return html == null ? null : RICH_TEXT.sanitize(html);
    }

    public String escape(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder builder = new StringBuilder(text.length() + 16);
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            switch (current) {
                case '&' -> builder.append("&amp;");
                case '<' -> builder.append("&lt;");
                case '>' -> builder.append("&gt;");
                case '"' -> builder.append("&#34;");
                case '\'' -> builder.append("&#39;");
                default -> builder.append(current);
            }
        }
        return builder.toString();
    }
}
