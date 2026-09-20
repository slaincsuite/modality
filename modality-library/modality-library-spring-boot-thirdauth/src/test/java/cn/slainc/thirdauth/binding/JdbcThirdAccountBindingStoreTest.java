package cn.slainc.thirdauth.binding;

import cn.slainc.thirdauth.ThirdPartyPlatform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcThirdAccountBindingStoreTest {

    private static JdbcTemplate jdbcTemplate;

    private static JdbcThirdAccountBindingStore store;

    @BeforeAll
    static void setUp() {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(
                new org.h2.Driver(), "jdbc:h2:mem:thirdauth-binding-test;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        store = new JdbcThirdAccountBindingStore(jdbcTemplate, "t_third_account_binding", true);
    }

    @AfterAll
    static void tearDown() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_third_account_binding");
    }

    @Test
    void tableCreatedAndSaveFindRoundTrip() {
        Long tables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE UPPER(table_name) = 'T_THIRD_ACCOUNT_BINDING'",
                Long.class);
        assertEquals(1L, tables);
    }

    @Test
    void findActiveByPlatformAndOpenId() {
        ThirdAccountBinding binding = ThirdAccountBinding.create(ThirdPartyPlatform.WECHAT, "open-jdbc", "union-jdbc",
                "7001");
        binding.setNickname("张三");
        store.save(binding);

        Optional<ThirdAccountBinding> found = store.findActive(ThirdPartyPlatform.WECHAT, "open-jdbc");
        assertTrue(found.isPresent());
        assertEquals("7001", found.get().getUserId());
        assertEquals("张三", found.get().getNickname());
        assertEquals(ThirdPartyPlatform.WECHAT, found.get().getPlatform());
        assertTrue(store.findActive(ThirdPartyPlatform.FEISHU, "open-jdbc").isEmpty());
    }

    @Test
    void unboundExcludedFromQueries() {
        ThirdAccountBinding binding = ThirdAccountBinding.create(ThirdPartyPlatform.DINGTALK, "open-unbind",
                null, "7002");
        store.save(binding);
        binding.setStatus(ThirdAccountBinding.Status.UNBOUND.name());
        store.save(binding);

        assertTrue(store.findActive(ThirdPartyPlatform.DINGTALK, "open-unbind").isEmpty());
        assertEquals(0, store.listByUser("7002").size());
    }

    @Test
    void queryByUnionIdAndUser() {
        store.save(ThirdAccountBinding.create(ThirdPartyPlatform.WECHAT, "open-u1", "union-shared", "7003"));
        store.save(ThirdAccountBinding.create(ThirdPartyPlatform.WECHAT, "open-u2", "union-other", "7003"));

        Optional<ThirdAccountBinding> byUnion = store.findByUnionId(ThirdPartyPlatform.WECHAT, "union-shared");
        assertTrue(byUnion.isPresent());
        assertEquals("7003", byUnion.get().getUserId());

        List<ThirdAccountBinding> byUser = store.listByUser("7003");
        assertEquals(2, byUser.size());
    }

    @Test
    void lastLoginAndProfilePersisted() {
        ThirdAccountBinding binding = ThirdAccountBinding.create(ThirdPartyPlatform.FEISHU, "open-profile", null,
                "7004");
        store.save(binding);
        binding.setNickname("新昵称");
        binding.setLastLoginAt(1700000000000L);
        store.save(binding);

        ThirdAccountBinding reloaded = store.findActive(ThirdPartyPlatform.FEISHU, "open-profile").orElseThrow();
        assertEquals("新昵称", reloaded.getNickname());
        assertEquals(1700000000000L, reloaded.getLastLoginAt());
    }
}
