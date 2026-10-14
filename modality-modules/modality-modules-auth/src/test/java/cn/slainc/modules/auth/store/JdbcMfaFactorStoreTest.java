package cn.slainc.modules.auth.store;

import cn.slainc.mfa.spi.MfaFactorSnapshot;
import cn.slainc.modules.auth.mfa.MfaFactorRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMfaFactorStoreTest {

    private JdbcTemplate jdbcTemplate;

    private JdbcMfaFactorStore store;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("mfaFactorStore" + System.nanoTime()).build();
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("auth-schema.sql"));
        }
        jdbcTemplate = new JdbcTemplate(dataSource);
        store = new JdbcMfaFactorStore(jdbcTemplate);
    }

    @Test
    void shouldInsertFindAndUpdateRecords() {
        MfaFactorRecord record = new MfaFactorRecord();
        record.setUserId(7L);
        record.setLoginType("TENANT_USER");
        record.setUsername("bob");
        record.setFactorType("TOTP");
        record.setSecretCipher("cipher");
        record.setSecretKeyId("key-1");
        record.setActivated(Boolean.FALSE);
        record.setFailCount(0);
        store.saveRecord(record);

        assertThat(record.getId()).isNotNull();
        MfaFactorRecord loaded = store.findRecord(7L, "TOTP");
        assertThat(loaded).isNotNull();
        assertThat(loaded.getUserId()).isEqualTo(7L);
        assertThat(loaded.getLoginType()).isEqualTo("TENANT_USER");
        assertThat(loaded.getUsername()).isEqualTo("bob");
        assertThat(loaded.getActivated()).isFalse();
        assertThat(loaded.getFailCount()).isZero();
        assertThat(loaded.getLastUsedAt()).isNull();
        assertThat(loaded.getLockedUntil()).isNull();

        LocalDateTime usedAt = LocalDateTime.now();
        loaded.setActivated(Boolean.TRUE);
        loaded.setLastUsedAt(usedAt);
        loaded.setRecoveryCodes("h1,h2");
        store.saveRecord(loaded);

        MfaFactorRecord updated = store.findRecord(7L, "TOTP");
        assertThat(updated.getActivated()).isTrue();
        assertThat(updated.getRecoveryCodes()).isEqualTo("h1,h2");
        assertThat(updated.getLastUsedAt()).isNotNull();
        assertThat(store.findRecord(7L, "TOTP").getId()).isEqualTo(record.getId());
    }

    @Test
    void shouldRoundTripTimestampsAsEpochMillis() {
        MfaFactorRecord record = new MfaFactorRecord();
        record.setUserId(8L);
        record.setFactorType("TOTP");
        record.setActivated(Boolean.TRUE);
        LocalDateTime lockedUntil = LocalDateTime.now().plusMinutes(5);
        record.setLockedUntil(lockedUntil);
        record.setFailCount(2);
        store.saveRecord(record);

        Long storedMillis = jdbcTemplate.queryForObject(
                "SELECT locked_until FROM t_mfa_factor WHERE user_id = 8", Long.class);
        assertThat(storedMillis).isPositive();
        assertThat(store.findRecord(8L, "TOTP").getLockedUntil())
                .isNotNull()
                .isAfter(LocalDateTime.now().plusMinutes(4));
    }

    @Test
    void shouldExposeTheLibraryPortOperations() {
        Long id = store.insert(new MfaFactorSnapshot(null, 9L, "TOTP", "cipher", "key-1", Boolean.FALSE,
                null, null, 0, null, 1L));
        assertThat(id).isNotNull();

        MfaFactorSnapshot found = store.find(9L, "TOTP");
        assertThat(found.userId()).isEqualTo(9L);
        assertThat(found.secretCipher()).isEqualTo("cipher");
        assertThat(found.tenantId()).isEqualTo(1L);
        assertThat(store.find(404L, "TOTP")).isNull();
        assertThat(store.find(null, "TOTP")).isNull();

        store.updateActivation(9L, "TOTP", true);
        assertThat(store.find(9L, "TOTP").activated()).isTrue();

        LocalDateTime lockedUntil = LocalDateTime.now().plusMinutes(3);
        store.updateFailure(9L, "TOTP", 4, lockedUntil);
        MfaFactorSnapshot failed = store.find(9L, "TOTP");
        assertThat(failed.failCount()).isEqualTo(4);
        assertThat(failed.lockedUntil()).isAfter(LocalDateTime.now().plusMinutes(2));

        LocalDateTime usedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        store.updateSuccess(9L, "TOTP", usedAt);
        MfaFactorSnapshot succeeded = store.find(9L, "TOTP");
        assertThat(succeeded.failCount()).isZero();
        assertThat(succeeded.lockedUntil()).isNull();
        assertThat(succeeded.lastUsedAt()).isEqualTo(usedAt);

        store.updateRecoveryCodes(9L, "TOTP", "h1,h2");
        assertThat(store.find(9L, "TOTP").recoveryCodes()).isEqualTo("h1,h2");

        assertThat(store.delete(9L, "TOTP")).isTrue();
        assertThat(store.delete(9L, "TOTP")).isFalse();
        assertThat(store.find(9L, "TOTP")).isNull();
    }

    @Test
    void shouldIgnorePortUpdatesForUnknownFactors() {
        store.updateActivation(99L, "TOTP", true);
        store.updateFailure(99L, "TOTP", 1, null);
        store.updateSuccess(99L, "TOTP", LocalDateTime.now());
        store.updateRecoveryCodes(99L, "TOTP", "h1");

        assertThat(store.find(99L, "TOTP")).isNull();
    }
}
