package cn.slainc.modules.policy.catalog;

import cn.slainc.modules.policy.service.catalog.PolicyCatalogService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyCatalogServiceTest {

    private final PolicyCatalogService catalog = new PolicyCatalogService();

    @Test
    void catalogCoversAllServiceFamiliesAndActualActionCodes() {
        assertThat(catalog.services()).extracting("code")
                .contains("upms", "auth", "policy", "audit", "open", "workorder",
                        "tenant", "infra", "job", "live");
        assertThat(catalog.supportsAction("modality:upms:user:permission-boundary")).isTrue();
        assertThat(catalog.supportsAction("modality:live:stream:check")).isTrue();
        assertThat(catalog.supportsAction("modality:infra:recycle:purge")).isTrue();
        assertThat(catalog.supportsAction("modality:policy:binding:export")).isTrue();
        assertThat(catalog.supportsAction("modality:missing:action")).isFalse();
    }

    @Test
    void resourceTreeCarriesSelectableResourcePatterns() {
        assertThat(catalog.resources()).hasSizeGreaterThanOrEqualTo(10);
        assertThat(catalog.resources().stream()
                .flatMap(service -> service.children().stream())
                .allMatch(resource -> resource.resource().startsWith("modality:"))).isTrue();
    }
}
