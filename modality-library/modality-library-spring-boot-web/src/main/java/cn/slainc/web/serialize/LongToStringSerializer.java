package cn.slainc.web.serialize;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class LongToStringSerializer extends ValueSerializer<Long> {

    @Override
    public void serialize(Long value, JsonGenerator generator, SerializationContext context) {
        if (value == null) {
            generator.writeNull();
        } else {
            generator.writeString(value.toString());
        }
    }
}
