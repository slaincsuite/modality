package cn.slainc.sso.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ServerSecretKeyConfiguredConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ServerOnlyConfig.class);

    @Test
    void blankSecretDisablesServerAssembly() {
        runner.run(context -> assertThat(context).doesNotHaveBean(ServerMarker.class));
    }

    @Test
    void configuredSecretEnablesServerAssembly() {
        runner.withPropertyValues(ServerSecretKeyConfiguredCondition.SECRET_KEY_PROPERTY + "=center-secret")
                .run(context -> assertThat(context).hasSingleBean(ServerMarker.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Conditional(ServerSecretKeyConfiguredCondition.class)
    static class ServerOnlyConfig {

        @Bean
        ServerMarker serverMarker() {
            return new ServerMarker();
        }
    }

    static class ServerMarker {
    }
}
