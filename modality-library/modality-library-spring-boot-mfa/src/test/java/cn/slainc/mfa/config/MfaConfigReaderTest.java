package cn.slainc.mfa.config;

import cn.slainc.core.config.DynamicConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MfaConfigReaderTest {

    private final MfaProperties properties = new MfaProperties();

    @Test
    void shouldFallBackToTheStaticPropertiesWithoutAConfigService() {
        MfaConfigReader reader = new MfaConfigReader(properties, null);

        assertThat(reader.enabled()).isTrue();
        assertThat(reader.issuer()).isEqualTo("modality");
        assertThat(reader.digits()).isEqualTo(6);
        assertThat(reader.periodSeconds()).isEqualTo(30);
        assertThat(reader.window()).isEqualTo(1);
        assertThat(reader.challengeTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(reader.recoveryCodeCount()).isEqualTo(10);
        assertThat(reader.maxFailCount()).isEqualTo(5);
        assertThat(reader.lockDuration()).isEqualTo(Duration.ofMinutes(10));
        assertThat(reader.requireForRoles()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPreferTheDynamicValues() {
        DynamicConfigService service = mock(DynamicConfigService.class);
        when(service.getBoolean("modality.mfa.enabled", true)).thenReturn(false);
        when(service.findString("modality.mfa.issuer")).thenReturn(Optional.of("acme"));
        when(service.getInt("modality.mfa.digits", 6)).thenReturn(8);
        when(service.getInt("modality.mfa.period-seconds", 30)).thenReturn(60);
        when(service.getInt("modality.mfa.window", 1)).thenReturn(2);
        when(service.findString("modality.mfa.challenge-ttl")).thenReturn(Optional.of("PT2M"));
        when(service.getInt("modality.mfa.max-fail-count", 5)).thenReturn(7);
        when(service.findString("modality.mfa.lock-duration")).thenReturn(Optional.of("PT1M"));

        MfaConfigReader reader = new MfaConfigReader(properties, providerOf(service));

        assertThat(reader.enabled()).isFalse();
        assertThat(reader.issuer()).isEqualTo("acme");
        assertThat(reader.digits()).isEqualTo(8);
        assertThat(reader.periodSeconds()).isEqualTo(60);
        assertThat(reader.window()).isEqualTo(2);
        assertThat(reader.challengeTtl()).isEqualTo(Duration.ofMinutes(2));
        assertThat(reader.maxFailCount()).isEqualTo(7);
        assertThat(reader.lockDuration()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldParseDurationsFromIsoTextOrPlainSeconds() {
        DynamicConfigService service = mock(DynamicConfigService.class);
        when(service.findString("modality.mfa.challenge-ttl")).thenReturn(Optional.of("PT90S"));
        when(service.findString("modality.mfa.lock-duration")).thenReturn(Optional.of("120"));

        MfaConfigReader reader = new MfaConfigReader(properties, providerOf(service));

        assertThat(reader.challengeTtl()).isEqualTo(Duration.ofSeconds(90));
        assertThat(reader.lockDuration()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFallBackWhenADynamicValueIsMissingOrMalformed() {
        DynamicConfigService service = mock(DynamicConfigService.class);
        when(service.findString("modality.mfa.issuer")).thenReturn(Optional.empty());
        when(service.findString("modality.mfa.challenge-ttl")).thenReturn(Optional.of("not-a-duration"));
        when(service.findString("modality.mfa.lock-duration")).thenReturn(Optional.of("  "));
        when(service.getInt("modality.mfa.digits", 6)).thenThrow(new IllegalStateException("不可用"));

        MfaConfigReader reader = new MfaConfigReader(properties, providerOf(service));

        assertThat(reader.issuer()).isEqualTo("modality");
        assertThat(reader.challengeTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(reader.lockDuration()).isEqualTo(Duration.ofMinutes(10));
        assertThat(reader.digits()).isEqualTo(6);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldParseRolesFromAJsonArrayOrACommaSeparatedText() {
        DynamicConfigService service = mock(DynamicConfigService.class);
        MfaConfigReader reader = new MfaConfigReader(properties, providerOf(service));

        when(service.findString("modality.mfa.require-for-roles")).thenReturn(Optional.of("[\"admin\",\"ops\"]"));
        assertThat(reader.requireForRoles()).containsExactly("admin", "ops");

        when(service.findString("modality.mfa.require-for-roles")).thenReturn(Optional.of("admin, ops"));
        assertThat(reader.requireForRoles()).containsExactly("admin", "ops");

        when(service.findString("modality.mfa.require-for-roles")).thenReturn(Optional.empty());
        properties.setRequireForRoles(List.of("auditor"));
        assertThat(reader.requireForRoles()).containsExactly("auditor");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<DynamicConfigService> providerOf(DynamicConfigService service) {
        ObjectProvider<DynamicConfigService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(service);
        return provider;
    }
}
