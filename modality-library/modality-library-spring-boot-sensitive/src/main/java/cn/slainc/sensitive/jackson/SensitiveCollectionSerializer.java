package cn.slainc.sensitive.jackson;

import cn.slainc.sensitive.core.SensitiveMasker;
import cn.slainc.sensitive.core.SensitiveRule;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.lang.reflect.Array;
import java.util.Collection;

public class SensitiveCollectionSerializer extends ValueSerializer<Object> {

    private final SensitiveMasker masker;

    private final SensitiveRule rule;

    public SensitiveCollectionSerializer(SensitiveMasker masker, SensitiveRule rule) {
        this.masker = masker;
        this.rule = rule;
    }

    @Override
    public void serialize(Object value, JsonGenerator generator, SerializationContext context) throws JacksonException {
        if (value == null) {
            generator.writeNull();
            return;
        }
        if (value instanceof Collection<?> collection) {
            generator.writeStartArray();
            for (Object element : collection) {
                writeElement(element, generator, context);
            }
            generator.writeEndArray();
            return;
        }
        if (value.getClass().isArray()) {
            generator.writeStartArray();
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                writeElement(Array.get(value, index), generator, context);
            }
            generator.writeEndArray();
            return;
        }
        context.findValueSerializer(value.getClass()).serialize(value, generator, context);
    }

    private void writeElement(Object element, JsonGenerator generator, SerializationContext context) throws JacksonException {
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
