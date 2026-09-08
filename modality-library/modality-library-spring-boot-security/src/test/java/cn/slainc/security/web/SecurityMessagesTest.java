package cn.slainc.security.web;

import cn.dev33.satoken.exception.NotLoginException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityMessagesTest {

    @Test
    void mapsEachNotLoginTypeToChineseMessage() {
        assertThat(SecurityMessages.notLoginMessage(NotLoginException.NOT_TOKEN)).isEqualTo("未能读取到有效 token");
        assertThat(SecurityMessages.notLoginMessage(NotLoginException.TOKEN_TIMEOUT)).isEqualTo("token 已过期");
        assertThat(SecurityMessages.notLoginMessage(NotLoginException.BE_REPLACED)).isEqualTo("token 已被顶下线");
        assertThat(SecurityMessages.notLoginMessage(NotLoginException.KICK_OUT)).isEqualTo("token 已被踢下线");
        assertThat(SecurityMessages.notLoginMessage(NotLoginException.TOKEN_FREEZE)).isEqualTo("token 已被冻结");
    }

    @Test
    void unknownTypeFallsBackToGenericMessage() {
        assertThat(SecurityMessages.notLoginMessage((String) null)).isEqualTo("未能读取到有效 token");
        assertThat(SecurityMessages.notLoginMessage("-99")).isEqualTo("未能读取到有效 token");
    }
}
