package cn.slainc.modules.upms.entity;

import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserRecordSerializationTest extends UpmsIntegrationTestBase {

    @Autowired
    private UserService userService;

    @Autowired
    private List<JsonMapperBuilderCustomizer> customizers;

    private JsonMapper mapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        customizers.forEach(customizer -> customizer.customize(builder));
        return builder.build();
    }

    @Test
    void passwordHashNeverSerialized() {
        UserRecord user = userService.create("json-secret", "密钥", "Secret#12345", "json@example.com",
                "13800001111", null, LoginType.TENANT_USER, 1L, "admin");
        String json = mapper().writeValueAsString(user);
        assertThat(json).doesNotContain("passwordHash");
        assertThat(json).doesNotContain(user.getPasswordHash());
    }

    @Test
    void phoneEmailAndAllowedIpsMaskedInJson() {
        UserRecord user = userService.create("json-mask", null, null, "json@example.com", "13800001111", null,
                LoginType.TENANT_USER, 1L, "admin");
        user.setAllowedIps("10.0.0.*,192.168.1.5");
        JsonNode node = mapper().readTree(mapper().writeValueAsString(user));
        assertThat(node.get("phone").asString()).isEqualTo("138****1111");
        assertThat(node.get("email").asString()).isEqualTo("j***@example.com");
        assertThat(node.get("allowedIps").asString()).doesNotContain("10.0.0.").doesNotContain("192.168.1.5");
    }

    @Test
    void maskingLeavesInternalGettersUntouched() {
        UserRecord user = userService.create("json-raw", null, null, "raw@example.com", "13800002222", null,
                LoginType.TENANT_USER, 1L, "admin");
        user.setAllowedIps("10.0.0.7");
        mapper().writeValueAsString(user);
        assertThat(user.getPhone()).isEqualTo("13800002222");
        assertThat(user.getEmail()).isEqualTo("raw@example.com");
        assertThat(user.getAllowedIps()).isEqualTo("10.0.0.7");
        assertThat(user.getPasswordHash()).startsWith("$2");
    }
}
