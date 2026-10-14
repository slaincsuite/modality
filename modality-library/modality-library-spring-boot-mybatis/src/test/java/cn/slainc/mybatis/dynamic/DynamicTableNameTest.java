package cn.slainc.mybatis.dynamic;

import cn.slainc.mybatis.MybatisTestApplication;
import cn.slainc.mybatis.base.Dict;
import cn.slainc.mybatis.dao.DictMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = MybatisTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "modality.mybatis.dynamic-table.enabled=true")
class DynamicTableNameTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DictMapper dictMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS t_dict ("
                + " id BIGINT PRIMARY KEY, tenant_id BIGINT, label VARCHAR(64), deleted INT DEFAULT 0)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS t_dict_shard ("
                + " id BIGINT PRIMARY KEY, tenant_id BIGINT, label VARCHAR(64), deleted INT DEFAULT 0)");
        jdbcTemplate.execute("DELETE FROM t_dict");
        jdbcTemplate.execute("DELETE FROM t_dict_shard");
        jdbcTemplate.update("INSERT INTO t_dict_shard VALUES (?, ?, ?, 0)", 9L, 1L, "分表数据");
    }

    @Test
    @DisplayName("动态表名作用域内查询打到分表")
    void dynamicTableNameRoutesQueryToShard() {
        assertTrue(dictMapper.selectList(null).isEmpty());

        List<Dict> sharded = DynamicTableNameHolder.call("t_dict", "t_dict_shard",
                () -> dictMapper.selectList(null));

        assertEquals(1, sharded.size());
        assertEquals("分表数据", sharded.get(0).getLabel());
    }

    @Test
    @DisplayName("作用域结束后恢复查询主表")
    void dynamicTableNameIsClearedAfterScope() {
        DynamicTableNameHolder.call("t_dict", "t_dict_shard", () -> dictMapper.selectList(null));

        assertTrue(dictMapper.selectList(null).isEmpty());
    }
}
