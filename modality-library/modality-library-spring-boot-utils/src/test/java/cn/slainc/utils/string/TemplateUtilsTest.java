package cn.slainc.utils.string;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateUtilsTest {

    @Test
    void renderBothPlaceholderStyles() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "modality");
        variables.put("version", 1);
        assertEquals("modality-1", TemplateUtils.render("$" + "{name}-#" + "{version}", variables));
    }

    @Test
    void keepsUnresolvedByDefault() {
        String template = "$" + "{missing}";
        assertEquals(template, TemplateUtils.render(template, Map.of()));
        assertEquals("", TemplateUtils.renderValues(template, Map.of()));
    }

    @Test
    void nullValueRendersEmpty() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("value", null);
        assertEquals("[]", TemplateUtils.render("[" + "$" + "{value}]", variables));
    }

    @Test
    void extractKeys() {
        assertTrue(TemplateUtils.extractKeys("$" + "{a} #" + "{b}").containsAll(java.util.List.of("a", "b")));
        assertEquals(2, TemplateUtils.extractKeys("$" + "{a} #" + "{b}").size());
    }
}
