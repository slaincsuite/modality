package cn.slainc.sensitive.jackson;

import cn.slainc.sensitive.core.SensitiveConfig;
import cn.slainc.sensitive.core.SensitiveMasker;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

public class SensitiveJacksonCustomizer implements JsonMapperBuilderCustomizer {

    public static final String MODULE_NAME = "modality-sensitive";

    private final SensitiveMasker masker;

    private final SensitiveConfig config;

    public SensitiveJacksonCustomizer(SensitiveMasker masker, SensitiveConfig config) {
        this.masker = masker;
        this.config = config;
    }

    @Override
    public void customize(JsonMapper.Builder builder) {
        SimpleModule module = new SimpleModule(MODULE_NAME);
        module.setSerializerModifier(new SensitiveSerializerModifier(masker, config));
        builder.addModule(module);
    }
}
