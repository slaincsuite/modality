package cn.slainc.captcha.support;

import cn.slainc.captcha.CaptchaData;
import tools.jackson.databind.json.JsonMapper;

public final class CaptchaDataCodec {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private CaptchaDataCodec() {
    }

    public static String write(CaptchaData data) {
        return MAPPER.writeValueAsString(data);
    }

    public static CaptchaData read(String json) {
        return MAPPER.readValue(json, CaptchaData.class);
    }
}
