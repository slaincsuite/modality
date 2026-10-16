package cn.slainc.thirdauth;

import cn.slainc.thirdauth.autoconfigure.ThirdAuthAutoConfiguration;
import cn.slainc.thirdauth.autoconfigure.ThirdAuthJdbcAutoConfiguration;
import cn.slainc.thirdauth.provider.ThirdPartyAuthProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ThirdAuthWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ThirdAuthAutoConfiguration.class,
                    ThirdAuthJdbcAutoConfiguration.class));

    @Test
    void defaultWiringProvidesInMemoryStoreAndFeishuProvider() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ThirdAuthService.class);
            assertThat(context).hasBean("feishuAuthProvider");
            assertThat(context.getBeansOfType(ThirdPartyAuthProvider.class)).hasSize(1);
        });
    }

    @Test
    void masterSwitchDisablesWiring() {
        runner.withPropertyValues("modality.thirdauth.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(ThirdAuthService.class));
    }

    @Test
    void customProviderOverridesBuiltIn() {
        FakeProvider custom = new FakeProvider(ThirdPartyPlatform.FEISHU);
        runner.withBean("feishuAuthProvider", cn.slainc.thirdauth.provider.ThirdPartyAuthProvider.class,
                        () -> custom)
                .run(context -> {
                    assertThat(context.getBean("feishuAuthProvider")).isSameAs(custom);
                    assertThat(context.getBeansOfType(ThirdPartyAuthProvider.class)).hasSize(1);
                });
    }
}
