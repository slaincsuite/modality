package cn.slainc.sensitive.jackson;

import cn.slainc.sensitive.core.SensitiveMasker;
import cn.slainc.sensitive.core.SensitiveRule;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class SensitiveValueSerializer extends ValueSerializer<Object> {

    private final SensitiveMasker masker;

    private final SensitiveRule rule;

    public SensitiveValueSerializer(SensitiveMasker masker, SensitiveRule rule) {
        this.masker = masker;
        this.rule = rule;
    }

    @Override
    public void serialize(Object value, JsonGenerator generator, SerializationContext context) throws JacksonException {
        if (value == null) {
            generator.writeNull();
            return;
        }
        if (value instanceof CharSequence text) {
            generator.writeString(masker.mask(text.toString(), rule));
            return;
        }
        context.findValueSerializer(value.getClass()).serialize(value, generator, context);
    }
}
