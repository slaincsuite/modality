package cn.slainc.tenant.autoconfigure;

import cn.slainc.tenant.context.TenantFilter;
import cn.slainc.tenant.context.TenantStatusChecker;
import cn.slainc.tenant.isolation.DatabaseTenantIsolationHandler;
import cn.slainc.tenant.isolation.RowTenantIsolationHandler;
import cn.slainc.tenant.isolation.SchemaTenantIsolationHandler;
import cn.slainc.tenant.isolation.TenantIsolationDispatcher;
import cn.slainc.tenant.lifecycle.DefaultTenantInitializer;
import cn.slainc.tenant.quota.TenantQuota;
import cn.slainc.tenant.quota.TenantQuotaService;
import cn.slainc.tenant.resolver.DomainTenantResolver;
import cn.slainc.tenant.resolver.HeaderTenantResolver;
import cn.slainc.tenant.resolver.ParamTenantResolver;
import cn.slainc.tenant.resolver.PathTenantResolver;
import cn.slainc.tenant.resolver.TenantResolverComposite;
import cn.slainc.tenant.lookup.TenantService;
import cn.slainc.tenant.support.TenantTableWhitelist;
import cn.slainc.web.filter.FilterOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;

class TenantAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantAutoConfiguration.class));

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantAutoConfiguration.class));

    @Test
    @DisplayName("modality.tenant.enabled 缺省为 true，默认即注册租户过滤能力")
    void enabledByDefault() {
        webRunner.run(context -> {
            assertThat(context).hasSingleBean(TenantFilter.class);
            assertThat(context).hasSingleBean(TenantResolverComposite.class);
            assertThat(context).hasSingleBean(TenantQuotaService.class);
            assertThat(context).hasSingleBean(TenantTableWhitelist.class);
        });
    }

    @Test
    @DisplayName("显式设置 modality.tenant.enabled=false 时不注册任何租户 Bean")
    void disabledExplicitly() {
        runner.withPropertyValues("modality.tenant.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(TenantFilter.class);
            assertThat(context).doesNotHaveBean(TenantResolverComposite.class);
            assertThat(context).doesNotHaveBean(TenantQuotaService.class);
            assertThat(context).doesNotHaveBean(TenantTableWhitelist.class);
        });
    }

    @Test
    @DisplayName("X-Tenant-Id 头解析器默认关闭，会话租户优先于客户端自报来源")
    void headerResolverDisabledByDefault() {
        webRunner.run(context -> {
            assertThat(context).doesNotHaveBean(HeaderTenantResolver.class);
            TenantResolverComposite composite = context.getBean(TenantResolverComposite.class);
            assertThat(composite.resolvers()).extracting(resolver -> resolver.getClass().getSimpleName())
                    .containsExactly("DomainTenantResolver", "PathTenantResolver", "ParamTenantResolver");
        });
    }

    @Test
    @DisplayName("会话租户解析优先级高于域名、路径、头与参数")
    void sessionResolverHasHighestPriority() {
        assertThat(cn.slainc.tenant.resolver.SessionTenantResolver.ORDER)
                .isLessThan(cn.slainc.tenant.resolver.DomainTenantResolver.ORDER)
                .isLessThan(cn.slainc.tenant.resolver.PathTenantResolver.ORDER)
                .isLessThan(HeaderTenantResolver.ORDER)
                .isLessThan(ParamTenantResolver.ORDER);
    }

    @Test
    @DisplayName("登记租户一致性过滤器，顺序在鉴权之后")
    void registersConsistencyFilter() {
        webRunner.run(context -> {
            assertThat(context).hasSingleBean(cn.slainc.tenant.context.TenantConsistencyFilter.class);
            FilterRegistrationBean<?> registration = context.getBean("tenantConsistencyFilterRegistration",
                    FilterRegistrationBean.class);
            assertThat(registration.getOrder()).isEqualTo(FilterOrder.TENANT_GUARD);
            assertThat(registration.getOrder()).isGreaterThan(FilterOrder.SA_TOKEN);
            assertThat(registration.getUrlPatterns()).containsExactly("/*");
        });
    }

    @Test
    @DisplayName("启用后在 Web 环境注册租户过滤器与全部解析器")
    void registersCapabilitiesWhenEnabled() {
        webRunner.withPropertyValues("modality.tenant.enabled=true",
                "modality.tenant.resolver.header.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(TenantFilter.class);
            assertThat(context).hasSingleBean(TenantResolverComposite.class);
            assertThat(context).hasSingleBean(HeaderTenantResolver.class);
            assertThat(context).hasSingleBean(DomainTenantResolver.class);
            assertThat(context).hasSingleBean(PathTenantResolver.class);
            assertThat(context).hasSingleBean(ParamTenantResolver.class);
            assertThat(context).hasSingleBean(RowTenantIsolationHandler.class);
            assertThat(context).hasSingleBean(SchemaTenantIsolationHandler.class);
            assertThat(context).hasSingleBean(DatabaseTenantIsolationHandler.class);
            assertThat(context).hasSingleBean(TenantIsolationDispatcher.class);
            assertThat(context).hasSingleBean(TenantQuotaService.class);
            assertThat(context).hasSingleBean(DefaultTenantInitializer.class);
            assertThat(context).hasSingleBean(TenantTableWhitelist.class);
            assertThat(context).hasSingleBean(TenantStatusChecker.class);
            assertThat(context).doesNotHaveBean(TenantService.class);

            FilterRegistrationBean<?> registration =
                    context.getBean("tenantFilterRegistration", FilterRegistrationBean.class);
            assertThat(registration.getOrder()).isEqualTo(FilterOrder.TENANT);
            assertThat(registration.getUrlPatterns()).containsExactly("/*");

            TenantResolverComposite composite = context.getBean(TenantResolverComposite.class);
            assertThat(composite.resolvers()).hasSize(4)
                    .extracting(resolver -> resolver.getClass().getSimpleName())
                    .containsExactly("DomainTenantResolver", "PathTenantResolver", "HeaderTenantResolver",
                            "ParamTenantResolver");
        });
    }

    @Test
    @DisplayName("非 Web 环境不注册过滤器，但解析器与隔离能力仍可用")
    void servletFilterOnlyInWebApplication() {
        runner.withPropertyValues("modality.tenant.enabled=true").run(context -> {
            assertThat(context).doesNotHaveBean(TenantFilter.class);
            assertThat(context).hasSingleBean(TenantResolverComposite.class);
            assertThat(context).hasSingleBean(TenantIsolationDispatcher.class);
        });
    }

    @Test
    @DisplayName("每一项能力都能单独关闭")
    void eachCapabilityCanBeDisabled() {
        webRunner.withPropertyValues("modality.tenant.enabled=true",
                "modality.tenant.filter.enabled=false",
                "modality.tenant.status-check.enabled=false",
                "modality.tenant.resolver.header.enabled=false",
                "modality.tenant.resolver.domain.enabled=false",
                "modality.tenant.resolver.path.enabled=false",
                "modality.tenant.resolver.param.enabled=false",
                "modality.tenant.isolation.schema.enabled=false",
                "modality.tenant.isolation.database.enabled=false",
                "modality.tenant.quota.enabled=false",
                "modality.tenant.lifecycle.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(TenantFilter.class);
            assertThat(context).doesNotHaveBean(HeaderTenantResolver.class);
            assertThat(context).doesNotHaveBean(DomainTenantResolver.class);
            assertThat(context).doesNotHaveBean(PathTenantResolver.class);
            assertThat(context).doesNotHaveBean(ParamTenantResolver.class);
            assertThat(context).doesNotHaveBean(SchemaTenantIsolationHandler.class);
            assertThat(context).doesNotHaveBean(DatabaseTenantIsolationHandler.class);
            assertThat(context).doesNotHaveBean(TenantQuotaService.class);
            assertThat(context).doesNotHaveBean(DefaultTenantInitializer.class);
            assertThat(context).doesNotHaveBean(TenantStatusChecker.class);
            assertThat(context).hasSingleBean(RowTenantIsolationHandler.class);
        });
    }

    @Test
    @DisplayName("状态拦截可单独关闭，关闭后过滤器不再持有校验器")
    void statusCheckCanBeDisabledIndependently() {
        webRunner.withPropertyValues("modality.tenant.enabled=true",
                "modality.tenant.status-check.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(TenantStatusChecker.class);
            assertThat(context).hasSingleBean(TenantFilter.class);
        });
    }

    @Test
    @DisplayName("状态拦截缺省开启且绑定配置项")
    void statusCheckEnabledByDefaultAndBindsOptions() {
        webRunner.withPropertyValues("modality.tenant.enabled=true",
                "modality.tenant.status-check.allowed-paths=/auth/login,/actuator/health",
                "modality.tenant.status-check.missing-tenant-policy=ALLOW",
                "modality.tenant.status-check.cache-expire-after-write=2m").run(context -> {
            assertThat(context).hasSingleBean(TenantStatusChecker.class);
            TenantStatusCheckProperties properties = context.getBean(TenantStatusCheckProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getAllowedPaths()).containsExactly("/auth/login", "/actuator/health");
            assertThat(properties.getMissingTenantPolicy())
                    .isEqualTo(TenantStatusCheckProperties.MissingTenantPolicy.ALLOW);
            assertThat(properties.getCacheExpireAfterWrite()).hasSeconds(120L);

            TenantStatusChecker checker = context.getBean(TenantStatusChecker.class);
            assertThat(checker.isAllowedPath("/auth/login")).isTrue();
            assertThat(checker.isAllowedPath("/api/orders")).isFalse();
        });
    }

    @Test
    @DisplayName("使用方可覆盖状态校验器 Bean")
    void userBeanOverridesStatusChecker() {
        TenantStatusChecker custom = new TenantStatusChecker(null, new TenantStatusCheckProperties());
        webRunner.withPropertyValues("modality.tenant.enabled=true")
                .withBean("tenantStatusChecker", TenantStatusChecker.class, () -> custom)
                .run(context -> {
                    assertThat(context).hasSingleBean(TenantStatusChecker.class);
                    assertThat(context.getBean(TenantStatusChecker.class)).isSameAs(custom);
                });
    }

    @Test
    @DisplayName("解析器配置项正确绑定到属性对象")
    void bindsResolverOptions() {
        runner.withPropertyValues("modality.tenant.enabled=true",
                "modality.tenant.resolver.header.enabled=true",
                "modality.tenant.resolver.header.name=X-Custom-Tenant",
                "modality.tenant.resolver.domain.root-domain=example.com",
                "modality.tenant.resolver.path.prefix=/openapi",
                "modality.tenant.resolver.param.name=tenant",
                "modality.tenant.isolation.schema.prefix=acme_",
                "modality.tenant.quota.warning-ratio=0.9",
                "modality.tenant.quota.defaults.max-users=50").run(context -> {
            assertThat(context.getBean(HeaderTenantResolver.class).headerName()).isEqualTo("X-Custom-Tenant");
            assertThat(context.getBean(DomainTenantResolver.class).rootDomain()).isEqualTo("example.com");
            assertThat(context.getBean(PathTenantResolver.class).prefix()).isEqualTo("/openapi");
            assertThat(context.getBean(ParamTenantResolver.class).paramName()).isEqualTo("tenant");
            assertThat(context.getBean(TenantQuotaService.class).limitOf(1L,
                    cn.slainc.tenant.quota.TenantQuotaDimension.USERS)).isEqualTo(50L);
        });
    }

    @Test
    @DisplayName("使用方自定义 Bean 可覆盖默认实现")
    void userBeansOverrideDefaults() {
        TenantQuotaService custom = new TenantQuotaService(TenantQuota.of(7L, 0L, 0L, 0L), 0.5D, null, null);
        runner.withPropertyValues("modality.tenant.enabled=true")
                .withBean("tenantQuotaService", TenantQuotaService.class, () -> custom)
                .run(context -> {
                    assertThat(context).hasSingleBean(TenantQuotaService.class);
                    assertThat(context.getBean(TenantQuotaService.class)).isSameAs(custom);
                });
    }
}
