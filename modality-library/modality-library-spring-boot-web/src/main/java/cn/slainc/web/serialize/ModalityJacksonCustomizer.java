package cn.slainc.web.serialize;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.module.SimpleModule;

public class ModalityJacksonCustomizer implements JsonMapperBuilderCustomizer {

    @Override
    public void customize(tools.jackson.databind.json.JsonMapper.Builder builder) {
        SimpleModule module = new SimpleModule("modality-jackson");
        module.addSerializer(Long.class, new LongToStringSerializer());
        builder.addModule(module);
        builder.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
        builder.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
