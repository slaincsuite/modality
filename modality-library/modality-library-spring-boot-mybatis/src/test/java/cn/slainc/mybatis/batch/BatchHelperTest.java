package cn.slainc.mybatis.batch;

import cn.slainc.core.context.TenantContext;
import cn.slainc.mybatis.MybatisTestApplication;
import cn.slainc.mybatis.entity.User;
import cn.slainc.mybatis.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = MybatisTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BatchHelperTest {

    @Autowired
    private BatchHelper batchHelper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS t_user ("
                + " id BIGINT PRIMARY KEY, tenant_id BIGINT, name VARCHAR(64), phone VARCHAR(32),"
                + " secret VARCHAR(512), remark CLOB, dept_id BIGINT, created_at TIMESTAMP, updated_at TIMESTAMP,"
                + " created_by VARCHAR(64), updated_by VARCHAR(64), deleted INT DEFAULT 0, version INT DEFAULT 0)");
        jdbcTemplate.execute("DELETE FROM t_user");
        TenantContext.clear();
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("批次加入调用方事务，外层回滚后不留数据")
    void batchInsertJoinsCallerTransactionRollback() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> template.execute(status -> {
            batchHelper.saveBatch(UserMapper.class, users(20), 5);
            throw new IllegalStateException("触发回滚");
        }));

        assertEquals(0L, userMapper.selectCount(null));
    }

    @Test
    @DisplayName("批次加入调用方事务并随其提交落库")
    void batchInsertJoinsCallerTransactionCommit() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        Integer inserted = template.execute(status -> batchHelper.saveBatch(UserMapper.class, users(20), 5));

        assertEquals(20, inserted);
        assertEquals(20L, userMapper.selectCount(null));
    }

    @Test
    @DisplayName("事务中已有普通会话时批次复用该会话而不切换执行器类型")
    void batchInsertReusesBoundSessionInsideTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        Integer inserted = template.execute(status -> {
            userMapper.selectCount(null);
            return batchHelper.saveBatch(UserMapper.class, users(10), 5);
        });

        assertEquals(10, inserted);
        assertEquals(10L, userMapper.selectCount(null));
    }

    @Test
    @DisplayName("无事务时保持原有分段提交行为")
    void batchInsertWithoutTransactionCommitsSegments() {
        int inserted = batchHelper.execute(users(7), 3,
                (session, item) -> session.getMapper(UserMapper.class).insert(item));

        assertEquals(7, inserted);
        assertEquals(7L, userMapper.selectCount(null));
    }

    @Test
    @DisplayName("空集合直接返回零且不开启会话")
    void emptyCollectionReturnsZero() {
        assertEquals(0, batchHelper.saveBatch(UserMapper.class, List.of(), 10));
        assertEquals(0, batchHelper.saveBatch(UserMapper.class, null, 10));
    }

    private static List<User> users(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            User user = new User();
            user.setId(500000L + i);
            user.setName("batch-" + i);
            users.add(user);
        }
        return users;
    }
}
