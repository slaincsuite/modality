package cn.slainc.mybatis.join;

import cn.slainc.mybatis.MybatisTestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = MybatisTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JoinTemplateTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JoinTemplate joinTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS t_join_dept (id BIGINT PRIMARY KEY, name VARCHAR(64))");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS t_join_emp (id BIGINT PRIMARY KEY, dept_id BIGINT,"
                + " name VARCHAR(64), salary INT)");
        jdbcTemplate.execute("DELETE FROM t_join_dept");
        jdbcTemplate.execute("DELETE FROM t_join_emp");
        jdbcTemplate.update("INSERT INTO t_join_dept VALUES (1, '研发部'), (2, '市场部')");
        jdbcTemplate.update("INSERT INTO t_join_emp VALUES (1, 1, 'bob', 30000), (2, 2, 'carol', 20000)");
    }

    @Test
    @DisplayName("先 where 后 join 时参数绑定到正确占位符")
    void whereBeforeJoinBindsParametersToMatchingPlaceholders() {
        JoinQuery sub = JoinQuery.from("t_join_emp").select("dept_id").where("name = ?", "bob");
        JoinQuery query = JoinQuery.from("t_join_dept")
                .select("t_join_dept.name AS dept_name")
                .where("t_join_dept.name = ?", "研发部")
                .subQueryJoin("staffed", sub, "staffed.dept_id = t_join_dept.id");

        List<JoinRow> rows = joinTemplate.selectList(query, JoinRow.class);

        assertEquals(1, rows.size());
        assertEquals("研发部", rows.get(0).getDeptName());
    }

    @Test
    @DisplayName("子查询条件不含匹配行时返回空结果")
    void unmatchedSubQueryReturnsEmpty() {
        JoinQuery sub = JoinQuery.from("t_join_emp").select("dept_id").where("name = ?", "dave");
        JoinQuery query = JoinQuery.from("t_join_dept")
                .select("t_join_dept.name AS dept_name")
                .where("t_join_dept.name = ?", "研发部")
                .subQueryJoin("staffed", sub, "staffed.dept_id = t_join_dept.id");

        assertTrue(joinTemplate.selectList(query, JoinRow.class).isEmpty());
    }

    @Test
    @DisplayName("LIKE 通配符被转义后按字面匹配")
    void likeWildcardsAreEscaped() {
        jdbcTemplate.update("INSERT INTO t_join_emp VALUES (3, 1, '100%off', 10000)");
        jdbcTemplate.update("INSERT INTO t_join_emp VALUES (4, 1, '100Xoff', 10000)");

        JoinQuery literal = JoinQuery.from("t_join_emp").select("name AS emp_name").contains("name", "100%");
        JoinQuery unescaped = JoinQuery.from("t_join_emp").select("name AS emp_name").where("name LIKE ?", "100%off");

        assertEquals(List.of("100%off"), employeeNames(literal));
        assertEquals(2, employeeNames(unescaped).size());
    }

    private List<String> employeeNames(JoinQuery query) {
        return joinTemplate.selectList(query, JoinRow.class).stream().map(JoinRow::getEmpName).toList();
    }

    public static class JoinRow {

        private String deptName;

        private String empName;

        public String getDeptName() {
            return deptName;
        }

        public void setDeptName(String deptName) {
            this.deptName = deptName;
        }

        public String getEmpName() {
            return empName;
        }

        public void setEmpName(String empName) {
            this.empName = empName;
        }
    }
}
