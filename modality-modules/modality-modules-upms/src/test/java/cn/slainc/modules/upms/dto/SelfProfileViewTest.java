package cn.slainc.modules.upms.dto;

import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.utils.json.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SelfProfileViewTest {

    @Test
    void selfProfileDoesNotExposeCredentialOrRestrictionFields() {
        UserRecord user = new UserRecord();
        user.setId(1L);
        user.setTenantId(2L);
        user.setUsername("alice");
        user.setNickname("Alice");
        user.setPasswordHash("secret-hash");
        user.setAllowedIps("10.0.0.0/8");
        user.setAllowedTimeRange("09:00-18:00");
        user.setAllowedDevices("PC");
        user.setExtJson("{\"secret\":\"value\"}");

        String json = JsonUtils.toJson(SelfProfileView.of(user));

        assertThat(json).contains("\"username\":\"alice\"");
        assertThat(json).doesNotContain("secret-hash", "10.0.0.0/8", "09:00-18:00",
                "allowedIps", "allowedTimeRange", "allowedDevices", "extJson", "passwordHash");
    }
}
