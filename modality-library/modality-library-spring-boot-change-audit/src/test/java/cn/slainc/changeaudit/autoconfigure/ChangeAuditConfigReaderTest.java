package cn.slainc.changeaudit.autoconfigure;

import cn.slainc.changeaudit.support.MapDynamicConfigService;
import cn.slainc.changeaudit.support.TestObjectProviders;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeAuditConfigReaderTest {

    private final ChangeAuditProperties properties = new ChangeAuditProperties();

    @Test
    void fallsBackToStaticPropertiesWithoutConfigService() {
        ChangeAuditConfigReader reader = new ChangeAuditConfigReader(properties, TestObjectProviders.empty());

        assertThat(reader.enabled()).isTrue();
        assertThat(reader.maxValueLength()).isEqualTo(512);
        assertThat(reader.ignoreFields()).containsExactly("createdBy", "createdAt", "updatedBy", "updatedAt",
                "version", "deleted");
        assertThat(reader.recordNoChange()).isFalse();
        assertThat(reader.async()).isFalse();
        assertThat(reader.maxExportRows()).isEqualTo(5000);
        assertThat(reader.javersDiffEngine()).isFalse();
    }

    @Test
    void dynamicValuesOverrideStaticProperties() {
        MapDynamicConfigService service = new MapDynamicConfigService()
                .put("modality.change-audit.enabled", "false")
                .put("modality.change-audit.max-value-length", "64")
                .put("modality.change-audit.ignore-fields", " createdBy , remark ")
                .put("modality.change-audit.record-no-change", "true")
                .put("modality.change-audit.max-export-rows", "1000")
                .put("modality.change-audit.diff-engine", "javers");
        ChangeAuditConfigReader reader = new ChangeAuditConfigReader(properties, TestObjectProviders.of(service));

        assertThat(reader.enabled()).isFalse();
        assertThat(reader.maxValueLength()).isEqualTo(64);
        assertThat(reader.ignoreFields()).containsExactly("createdBy", "remark");
        assertThat(reader.recordNoChange()).isTrue();
        assertThat(reader.maxExportRows()).isEqualTo(1000);
        assertThat(reader.javersDiffEngine()).isTrue();
    }

    @Test
    void blankOrInvalidDynamicValuesFallBackToStaticProperties() {
        MapDynamicConfigService service = new MapDynamicConfigService()
                .put("modality.change-audit.max-value-length", "not-a-number")
                .put("modality.change-audit.ignore-fields", "  ")
                .put("modality.change-audit.diff-engine", "  ");
        ChangeAuditConfigReader reader = new ChangeAuditConfigReader(properties, TestObjectProviders.of(service));

        assertThat(reader.maxValueLength()).isEqualTo(512);
        assertThat(reader.ignoreFields()).isEqualTo(properties.getIgnoreFields());
        assertThat(reader.javersDiffEngine()).isFalse();
    }

    @Test
    void configServiceFailuresFallBackToStaticProperties() {
        MapDynamicConfigService broken = new MapDynamicConfigService() {
            @Override
            public Optional<String> findString(String key) {
                throw new IllegalStateException("boom");
            }
        };
        ChangeAuditConfigReader reader = new ChangeAuditConfigReader(properties, TestObjectProviders.of(broken));

        assertThat(reader.enabled()).isTrue();
        assertThat(reader.maxValueLength()).isEqualTo(512);
    }

    @Test
    void toleratesMissingPropertiesInstance() {
        ChangeAuditConfigReader reader = new ChangeAuditConfigReader(null, null);

        assertThat(reader.enabled()).isTrue();
        assertThat(reader.maxValueLength()).isEqualTo(512);
        assertThat(reader.properties()).isNotNull();
    }
}
