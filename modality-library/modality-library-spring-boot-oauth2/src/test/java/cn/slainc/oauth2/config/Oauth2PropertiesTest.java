package cn.slainc.oauth2.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Oauth2PropertiesTest {

    @Test
    void guardShipsWithNonZeroDefaultQpsLimit() {
        Oauth2Properties properties = new Oauth2Properties();
        assertThat(properties.getGuard().isEnabled()).isTrue();
        assertThat(properties.getGuard().getDefaultQpsLimit()).isPositive();
    }
}
