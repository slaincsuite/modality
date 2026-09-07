package cn.slainc.sensitive.xss;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XssSanitizerTest {

    private final XssSanitizer sanitizer = new XssSanitizer();

    @Test
    void removesScriptBlock() {
        String cleaned = sanitizer.clean("<script>alert('xss')</script>");
        assertEquals("", cleaned);
        assertFalse(cleaned.contains("script"));
        assertFalse(cleaned.contains("alert"));
    }

    @Test
    void removesScriptButKeepsSurroundingText() {
        assertEquals("helloworld", sanitizer.clean("hello<script>alert(1)</script>world"));
    }

    @Test
    void removesEventHandlerAttributes() {
        assertEquals("", sanitizer.clean("<img src=x onerror=alert(1)>"));
        assertEquals("<p>段落</p>", sanitizer.cleanRich("<p onclick=\"steal()\">段落</p>"));
    }

    @Test
    void richPolicyKeepsWhitelistedTagsAndDropsJavascriptUrl() {
        String cleaned = sanitizer.cleanRich("<b>bold</b><a href=\"javascript:alert(1)\">link</a>");
        assertEquals("<b>bold</b>link", cleaned);
        assertFalse(cleaned.contains("javascript"));
    }

    @Test
    void plainPolicyDropsAllTags() {
        assertEquals("boldlink", sanitizer.clean("<b>bold</b><a href=\"https://example.com\">link</a>"));
    }

    @Test
    void escapesHtmlEntities() {
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", sanitizer.escape("<script>alert(1)</script>"));
        assertEquals("a &amp; b &#34;c&#34; &#39;d&#39;", sanitizer.escape("a & b \"c\" 'd'"));
        assertTrue(sanitizer.escape("<script>").contains("&lt;"));
    }

    @Test
    void keepsNullUntouched() {
        assertNull(sanitizer.clean(null));
        assertNull(sanitizer.cleanRich(null));
        assertNull(sanitizer.escape(null));
    }
}
