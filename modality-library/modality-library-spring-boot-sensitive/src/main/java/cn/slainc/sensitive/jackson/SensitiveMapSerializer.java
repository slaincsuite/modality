package cn.slainc.sensitive.jackson;

import cn.slainc.sensitive.SensitiveException;
import cn.slainc.sensitive.core.SensitiveMasker;
import cn.slainc.sensitive.core.SensitiveRule;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.util.Map;

public class SensitiveMapSerializer extends ValueSerializer<Object> {

    private final SensitiveMasker masker;

    private final SensitiveRule rule;

    public SensitiveMapSerializer(SensitiveMasker masker, SensitiveRule rule) {
        this.masker = masker;
        this.rule = rule;
    }

    @Override
    public void serialize(Object value, JsonGenerator generator, SerializationContext context) throws JacksonException {
        if (value == null) {
            generator.writeNull();
            return;
        }
        if (!(value instanceof Map<?, ?> map)) {
            context.findValueSerializer(value.getClass()).serialize(value, generator, context);
            return;
        }
        generator.writeStartObject();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            writeKey(entry.getKey(), generator, context);
            writeValue(entry.getValue(), generator, context);
        }
        generator.writeEndObject();
    }

    private void writeKey(Object key, JsonGenerator generator, SerializationContext context) throws JacksonException {
        if (key == null) {
            throw new SensitiveException("Map 中存在 null 键，无法序列化");
        }
        if (key instanceof CharSequence text) {
            generator.writeName(text.toString());
            return;
        }
        context.findKeySerializer(key.getClass(), null).serialize(key, generator, context);
    }

    private void writeValue(Object element, JsonGenerator generator, SerializationContext context) throws JacksonException {
        if (element == null) {
            generator.writeNull();
            return;
        }
        if (element instanceof CharSequence text) {
            generator.writeString(masker.mask(text.toString(), rule));
            return;
        }
        context.findValueSerializer(element.getClass()).serialize(element, generator, context);
    }
}
