package cn.slainc.mybatis;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.mybatis.batch.BatchHelper;
import cn.slainc.mybatis.entity.Dict;
import cn.slainc.mybatis.entity.User;
import cn.slainc.mybatis.mapper.DictMapper;
import cn.slainc.mybatis.mapper.UserMapper;
import cn.slainc.mybatis.page.KeysetResult;
import cn.slainc.mybatis.page.PageUtils;
import cn.slainc.mybatis.sql.SqlAnalyzer;
import cn.slainc.mybatis.tenant.TenantHelper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = MybatisTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MybatisIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DictMapper dictMapper;

    @Autowired
    private BatchHelper batchHelper;

    @Autowired
    private SqlAnalyzer sqlAnalyzer;

    @Autowired
    private cn.slainc.mybatis.config.MybatisConfigRefresher configRefresher;

    @Autowired
    private cn.slainc.mybatis.properties.MybatisProperties mybatisProperties;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS t_user ("
                + " id BIGINT PRIMARY KEY, tenant_id BIGINT, name VARCHAR(64), phone VARCHAR(32),"
                + " secret VARCHAR(512), remark CLOB, dept_id BIGINT, created_at TIMESTAMP, updated_at TIMESTAMP,"
                + " created_by VARCHAR(64), updated_by VARCHAR(64), deleted INT DEFAULT 0, version INT DEFAULT 0)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS t_dict ("
                + " id BIGINT PRIMARY KEY, tenant_id BIGINT, label VARCHAR(64), deleted INT DEFAULT 0)");
        jdbcTemplate.execute("DELETE FROM t_user");
        jdbcTemplate.execute("DELETE FROM t_dict");
        TenantContext.clear();
        UserContext.clear();
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        UserContext.clear();
    }

    @Test
    @DisplayName("物理分页返回正确的总数与页数据")
    void paginationWorks() {
        for (int i = 1; i <= 25; i++) {
            insertUser(String.format("user-%02d", i), 1L, "100");
        }
        Page<User> page = userMapper.selectPage(new Page<>(2, 10), new QueryWrapper<User>().orderByAsc("name"));
        assertEquals(25L, page.getTotal());
        assertEquals(10, page.getRecords().size());
        assertEquals(3L, page.getPages());
        assertEquals("user-11", page.getRecords().get(0).getName());
        assertEquals("user-20", page.getRecords().get(9).getName());
    }

    @Test
    @DisplayName("超大页大小被 maxLimit 截断")
    void paginationRespectsMaxLimit() {
        for (int i = 1; i <= 5; i++) {
            insertUser("user-" + i, 1L, "100");
        }
        Page<User> page = userMapper.selectPage(new Page<>(1, 100000), null);
        assertEquals(5L, page.getTotal());
        assertTrue(page.getSize() <= 500, "分页大小应被上限截断，实际: " + page.getSize());
    }

    @Test
    @DisplayName("跨租户查询被正确拦截")
    void tenantIsolationBlocksCrossTenantReads() {
        insertUser("tenant-1-a", 1L, "100");
        insertUser("tenant-1-b", 1L, "100");
        insertUser("tenant-1-c", 1L, "100");
        insertUser("tenant-2-a", 2L, "100");
        insertUser("tenant-2-b", 2L, "100");

        TenantContext.set(1L);
        List<User> tenantOne = userMapper.selectList(null);
        assertEquals(3, tenantOne.size());
        assertTrue(tenantOne.stream().allMatch(user -> Long.valueOf(1L).equals(user.getTenantId())));

        TenantContext.set(2L);
        List<User> tenantTwo = userMapper.selectList(null);
        assertEquals(2, tenantTwo.size());
        assertTrue(tenantTwo.stream().allMatch(user -> Long.valueOf(2L).equals(user.getTenantId())));

        TenantContext.set(3L);
        assertTrue(userMapper.selectList(null).isEmpty());
    }

    @Test
    @DisplayName("超级管理员可跨租户查询")
    void superAdminSeesAllTenants() {
        insertUser("tenant-1-a", 1L, "100");
        insertUser("tenant-2-a", 2L, "100");

        UserContext.set(new UserContext.UserInfo(1L, "admin", LoginType.PLATFORM_ADMIN, Set.of(), Set.of(), true));
        assertEquals(2, userMapper.selectList(null).size());
    }

    @Test
    @DisplayName("编程式忽略租户与 @IgnoreTenant 注解均生效")
    void ignoreTenantWorks() {
        insertUser("tenant-1-a", 1L, "100");
        insertUser("tenant-2-a", 2L, "100");

        TenantContext.set(1L);
        assertEquals(1, userMapper.selectList(null).size());
        assertEquals(2, TenantHelper.ignore(() -> userMapper.selectList(null)).size());
        assertEquals(2, userMapper.selectAllIgnoringTenant().size());
        assertEquals(1, userMapper.selectList(null).size());
    }

    @Test
    @DisplayName("指定租户查询通过 callWith 临时切换")
    void callWithSwitchesTenantTemporarily() {
        insertUser("tenant-1-a", 1L, "100");
        insertUser("tenant-2-a", 2L, "100");

        TenantContext.set(1L);
        List<User> other = TenantContext.callWith(2L, () -> userMapper.selectList(null));
        assertEquals(1, other.size());
        assertEquals(2L, other.get(0).getTenantId());
        assertEquals(1, userMapper.selectList(null).size());
    }

    @Test
    @DisplayName("白名单表不追加租户条件")
    void whitelistTableIsNotFiltered() {
        Dict first = new Dict();
        first.setId(1L);
        first.setTenantId(1L);
        first.setLabel("租户一字典");
        Dict second = new Dict();
        second.setId(2L);
        second.setTenantId(2L);
        second.setLabel("租户二字典");
        dictMapper.insert(first);
        dictMapper.insert(second);

        TenantContext.set(1L);
        assertEquals(2, dictMapper.selectList(null).size());
    }

    @Test
    @DisplayName("慢 SQL 阈值与租户白名单可运行时热更新")
    void dynamicConfigurationTakesEffectImmediately() {
        Dict first = new Dict();
        first.setId(21L);
        first.setTenantId(1L);
        first.setLabel("租户一");
        Dict second = new Dict();
        second.setId(22L);
        second.setTenantId(2L);
        second.setLabel("租户二");
        dictMapper.insert(first);
        dictMapper.insert(second);

        TenantContext.set(1L);
        assertEquals(2, dictMapper.selectList(null).size());

        configRefresher.apply(cn.slainc.mybatis.config.MybatisConfigRefresher.KEY_TENANT_IGNORE_TABLES,
                "t_user", false);
        assertEquals(1, dictMapper.selectList(null).size());

        configRefresher.apply(cn.slainc.mybatis.config.MybatisConfigRefresher.KEY_TENANT_IGNORE_TABLES,
                "t_dict", false);
        assertEquals(2, dictMapper.selectList(null).size());

        long before = mybatisProperties.getSqlLog().getSlowThreshold();
        configRefresher.apply(cn.slainc.mybatis.config.MybatisConfigRefresher.KEY_SLOW_THRESHOLD,
                "12345", false);
        assertEquals(12345L, mybatisProperties.getSqlLog().getSlowThreshold());
        configRefresher.apply(cn.slainc.mybatis.config.MybatisConfigRefresher.KEY_SLOW_THRESHOLD,
                String.valueOf(before), false);
    }

    @Test
    @DisplayName("慢 SQL 被统计并按类型汇总")
    void slowSqlIsCaptured() {
        sqlAnalyzer.reset();
        insertUser("slow-a", 1L, "100");
        userMapper.selectList(null);

        assertFalse(sqlAnalyzer.topSlow().isEmpty(), "慢 SQL 排行不应为空");
        assertTrue(sqlAnalyzer.topSlow().get(0).elapsedMillis() >= 0);
        Map<String, Long> byType = sqlAnalyzer.countByType();
        assertTrue(byType.getOrDefault("SELECT", 0L) >= 1, "SQL 统计: " + byType);
        assertTrue(byType.getOrDefault("INSERT", 0L) >= 1, "SQL 统计: " + byType);
        assertTrue(sqlAnalyzer.total() >= 2);
        assertFalse(sqlAnalyzer.averageElapsedByType().isEmpty());
    }

    @Test
    @DisplayName("慢 SQL 可给出索引建议")
    void indexSuggestionWorks() {
        insertUser("index-a", 1L, "100");
        List<String> suggestions = sqlAnalyzer.suggestIndexes("SELECT * FROM t_user WHERE name = 'index-a'");
        assertFalse(suggestions.isEmpty());
    }

    @Test
    @DisplayName("自动填充审计字段、租户、逻辑删除与版本号")
    void autoFillWorks() {
        UserContext.set(new UserContext.UserInfo(7L, "alice", LoginType.PLATFORM_ADMIN, Set.of(), Set.of(), false));
        User user = new User();
        user.setId(9001L);
        user.setName("auto-fill");
        userMapper.insert(user);

        User loaded = userMapper.selectById(9001L);
        assertNotNull(loaded.getCreatedAt());
        assertNotNull(loaded.getUpdatedAt());
        assertEquals("alice", loaded.getCreatedBy());
        assertEquals("alice", loaded.getUpdatedBy());
        assertEquals(0, loaded.getDeleted());
        assertEquals(0, loaded.getVersion());
        assertEquals(1L, loaded.getTenantId());
    }

    @Test
    @DisplayName("逻辑删除后查询不再返回该行")
    void logicalDeleteFiltersRows() {
        insertUser("to-delete", 1L, "100");
        Long id = userMapper.selectList(null).get(0).getId();

        userMapper.deleteById(id);

        assertEquals(0, userMapper.selectList(null).size());
        Integer raw = jdbcTemplate.queryForObject("SELECT deleted FROM t_user WHERE id = " + id, Integer.class);
        assertEquals(1, raw);
    }

    @Test
    @DisplayName("乐观锁在更新时递增版本号")
    void optimisticLockIncrementsVersion() {
        insertUser("lock-me", 1L, "100");
        User user = userMapper.selectList(null).get(0);
        assertEquals(0, user.getVersion());

        user.setName("lock-me-updated");
        userMapper.updateById(user);

        User reloaded = userMapper.selectById(user.getId());
        assertEquals("lock-me-updated", reloaded.getName());
        assertEquals(1, reloaded.getVersion());
    }

    @Test
    @DisplayName("全表更新与删除被拦截")
    void fullTableUpdateIsBlocked() {
        Dict dict = new Dict();
        dict.setId(10L);
        dict.setTenantId(1L);
        dict.setLabel("字典");
        dictMapper.insert(dict);

        assertThrows(RuntimeException.class, () -> dictMapper.delete(null));
    }

    @Test
    @DisplayName("加密字段落库为密文、读取时透明解密")
    void fieldEncryptionRoundTrip() {
        User user = new User();
        user.setId(7001L);
        user.setName("encrypted");
        user.setSecret("top-secret-value");
        userMapper.insert(user);

        String raw = userMapper.selectRawSecret(7001L);
        assertNotEquals("top-secret-value", raw);
        assertFalse(raw.contains("top-secret-value"));

        User loaded = userMapper.selectById(7001L);
        assertEquals("top-secret-value", loaded.getSecret());
    }

    @Test
    @DisplayName("按加密列等值查询能命中已写入的行")
    void queryByEncryptedColumnMatchesInsertedRow() {
        User user = new User();
        user.setId(7004L);
        user.setName("by-secret");
        user.setSecret("lookup-value");
        userMapper.insert(user);

        User probe = new User();
        probe.setSecret("lookup-value");
        List<User> found = userMapper.selectBySecret(probe);

        assertEquals(1, found.size());
        assertEquals(7004L, found.get(0).getId());
        assertEquals("lookup-value", found.get(0).getSecret());
        assertEquals("lookup-value", probe.getSecret());
    }

    @Test
    @DisplayName("脱敏字段在读取结果中被掩码")
    void sensitiveFieldIsMasked() {
        User user = new User();
        user.setId(7002L);
        user.setName("masked");
        user.setPhone("13800001111");
        userMapper.insert(user);

        User loaded = userMapper.selectById(7002L);
        assertEquals("138****1111", loaded.getPhone());
    }

    @Test
    @DisplayName("数据权限按当前用户的 created_by 过滤")
    void dataScopeRestrictsByCreator() {
        insertUser("by-100", 1L, "100");
        insertUser("by-200", 1L, "200");

        UserContext.set(new UserContext.UserInfo(100L, "user-100", LoginType.TENANT_USER, Set.of(), Set.of(), false));
        List<User> scoped = userMapper.selectWithDataScope();
        assertEquals(1, scoped.size());
        assertEquals("by-100", scoped.get(0).getName());

        UserContext.set(new UserContext.UserInfo(200L, "user-200", LoginType.TENANT_USER, Set.of(), Set.of(), false));
        assertEquals("by-200", userMapper.selectWithDataScope().get(0).getName());

        UserContext.set(new UserContext.UserInfo(1L, "admin", LoginType.PLATFORM_ADMIN, Set.of(), Set.of(), true));
        assertEquals(2, userMapper.selectWithDataScope().size());
    }

    @Test
    @DisplayName("JSON 类型处理器完成对象与列的双向转换")
    void jsonTypeHandlerRoundTrip() {
        User user = new User();
        user.setId(7003L);
        user.setName("json");
        user.setRemark(Map.of("level", 3, "tag", "vip"));
        userMapper.insert(user);

        User loaded = userMapper.selectById(7003L);
        assertEquals(3, loaded.getRemark().get("level"));
        assertEquals("vip", loaded.getRemark().get("tag"));
    }

    @Test
    @DisplayName("批量插入按批次提交")
    void batchInsertWorks() {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            User user = new User();
            user.setId(100000L + i);
            user.setName("batch-" + i);
            users.add(user);
        }
        int inserted = batchHelper.saveBatch(UserMapper.class, users, 200);

        assertEquals(1000, inserted);
        assertEquals(1000L, userMapper.selectCount(null));
    }

    @Test
    @DisplayName("Keyset 游标分页不依赖 OFFSET")
    void keysetPaginationWorks() {
        for (int i = 1; i <= 25; i++) {
            insertUser(String.format("k-%02d", i), 1L, "100");
        }
        KeysetResult<User> first = PageUtils.keyset(userMapper,
                new QueryWrapper<User>().orderByAsc("name"), "name", null, true, 10);
        assertEquals(10, first.records().size());
        assertTrue(first.hasNext());
        assertEquals("k-10", first.nextCursor());

        KeysetResult<User> second = PageUtils.keyset(userMapper,
                new QueryWrapper<User>(), "name", first.nextCursor(), true, 10);
        assertEquals(10, second.records().size());
        assertEquals("k-11", second.records().get(0).getName());
    }

    private void insertUser(String name, Long tenantId, String createdBy) {
        User user = new User();
        user.setId((long) (Math.abs(name.hashCode()) % 100000) + 1L);
        user.setName(name);
        user.setCreatedBy(createdBy);
        user.setTenantId(tenantId);
        userMapper.insert(user);
    }
}
