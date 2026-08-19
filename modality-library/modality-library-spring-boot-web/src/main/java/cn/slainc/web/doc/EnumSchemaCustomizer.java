package cn.slainc.web.doc;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EnumSchemaCustomizer implements OpenApiCustomizer {

    private static final String ENUM_PREFIX = "可选值：";

    private static final int MAX_DEPTH = 5;

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        openApi.getComponents().getSchemas().values().forEach(schema -> describe(schema, 0));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void describe(Schema schema, int depth) {
        if (schema == null || depth > MAX_DEPTH) {
            return;
        }
        appendEnumValues(schema);
        if (schema.getItems() != null) {
            describe(schema.getItems(), depth + 1);
        }
        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            properties.values().forEach(property -> describe(property, depth + 1));
        }
    }

    private void appendEnumValues(Schema<?> schema) {
        List<?> values = schema.getEnum();
        if (values == null || values.isEmpty()) {
            return;
        }
        String description = schema.getDescription();
        if (description != null && description.contains(ENUM_PREFIX)) {
            return;
        }
        String enumText = ENUM_PREFIX + values.stream().map(String::valueOf).collect(Collectors.joining("、"));
        schema.setDescription(description == null || description.isBlank() ? enumText : description + "；" + enumText);
    }
}
