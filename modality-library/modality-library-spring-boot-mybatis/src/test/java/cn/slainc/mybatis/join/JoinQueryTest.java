package cn.slainc.mybatis.join;

import cn.slainc.mybatis.exception.MybatisException;
import cn.slainc.mybatis.support.LikeUtils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinQueryTest {

    @Test
    @DisplayName("先 where 后 join 时参数仍按渲染顺序排列")
    void parametersFollowRenderOrderWhenWhereAddedBeforeJoin() {
        JoinQuery sub = JoinQuery.from("t_order").select("user_id").where("amount > ?", 100);
        JoinQuery query = JoinQuery.from("t_user")
                .select("t_user.name")
                .where("t_user.name = ?", "alice")
                .subQueryJoin("paid", sub, "paid.user_id = t_user.id");

        String sql = query.sql();
        assertTrue(sql.indexOf("JOIN (") < sql.indexOf("WHERE "), "渲染顺序应为 joins 在 where 之前: " + sql);
        assertEquals(List.of(100, "alice"), query.parameters());
    }

    @Test
    @DisplayName("countSql 与 sql 的参数顺序完全一致")
    void countSqlKeepsSameParameterOrder() {
        JoinQuery sub = JoinQuery.from("t_order").select("user_id").where("amount > ?", 100);
        JoinQuery query = JoinQuery.from("t_user")
                .where("t_user.name = ?", "alice")
                .subQueryJoin("paid", sub, "paid.user_id = t_user.id")
                .having("COUNT(*) > ?", 3)
                .orderBy("t_user.name")
                .limit(10)
                .offset(20);

        assertEquals(List.of(100, "alice", 3), query.parameters());
        assertTrue(query.countSql().contains("JOIN ("));
        assertTrue(query.countSql().indexOf("JOIN (") < query.countSql().indexOf("WHERE "));
    }

    @Test
    @DisplayName("in 与 subQueryIn 的参数跟随各自片段")
    void inClauseParametersKeepTheirOwnPosition() {
        JoinQuery sub = JoinQuery.from("t_role").select("user_id").where("role = ?", "admin");
        JoinQuery query = JoinQuery.from("t_user")
                .where("t_user.status = ?", 1)
                .in("t_user.id", List.of(11L, 12L))
                .subQueryIn("t_user.dept_id", sub);

        assertEquals(List.of(1, 11L, 12L, "admin"), query.parameters());
        String sql = query.sql();
        assertTrue(sql.indexOf("status = ?") < sql.indexOf("id IN (?, ?)"));
        assertTrue(sql.indexOf("id IN (?, ?)") < sql.indexOf("dept_id IN ("));
    }

    @Test
    @DisplayName("空集合生成恒假条件且不追加参数")
    void emptyInProducesAlwaysFalseCondition() {
        JoinQuery query = JoinQuery.from("t_user").where("t_user.name = ?", "alice").in("t_user.id", List.of());

        assertEquals(List.of("alice"), query.parameters());
        assertTrue(query.sql().contains("1 = 0"));
    }

    @Test
    @DisplayName("参数对象的后续修改不影响已并入子查询的参数")
    void subQueryParametersAreCapturedAtCallTime() {
        JoinQuery sub = JoinQuery.from("t_order").where("amount > ?", 1);
        JoinQuery query = JoinQuery.from("t_user").subQueryJoin("o", sub, "o.user_id = t_user.id");
        sub.where("status = ?", 2);

        assertEquals(List.of(1), query.parameters());
        assertEquals(List.of(1, 2), sub.parameters());
    }

    @Test
    @DisplayName("嵌套子查询按渲染顺序递归收集参数")
    void nestedSubQueryParametersAreOrdered() {
        JoinQuery inner = JoinQuery.from("t_role").where("role = ?", "admin");
        JoinQuery outer = JoinQuery.from("t_user_role")
                .where("enabled = ?", true)
                .subQueryIn("role_id", inner);
        JoinQuery query = JoinQuery.from("t_user")
                .where("name = ?", "alice")
                .subQueryJoin("r", outer, "r.user_id = t_user.id");

        assertEquals(List.of(true, "admin", "alice"), query.parameters());
    }

    @Test
    @DisplayName("LIKE 条件转义通配符并附带 ESCAPE 子句")
    void likeConditionsEscapeWildcards() {
        String escape = String.valueOf(LikeUtils.DEFAULT_ESCAPE);
        JoinQuery query = JoinQuery.from("t_user")
                .contains("t_user.name", "100%")
                .startsWith("t_user.code", "A_");

        assertEquals(List.of("%100" + escape + "%%", "A" + escape + "_%"), query.parameters());
        assertTrue(query.sql().contains("t_user.name LIKE ? ESCAPE '" + escape + "'"));
        assertTrue(query.sql().contains("t_user.code LIKE ? ESCAPE '" + escape + "'"));
    }

    @Test
    @DisplayName("LIKE 匹配值为空时明确报错")
    void likeWithNullValueIsRejected() {
        JoinQuery query = JoinQuery.from("t_user");

        assertThrows(MybatisException.class, () -> query.contains("t_user.name", null));
    }

    @Test
    @DisplayName("无参数查询返回空参数列表")
    void queryWithoutParametersReturnsEmptyList() {
        JoinQuery query = JoinQuery.from("t_user").select("id").innerJoin("t_dept", "t_dept.id = t_user.dept_id");

        assertEquals(List.of(), query.parameters());
        assertTrue(query.sql().startsWith("SELECT id FROM t_user INNER JOIN t_dept ON t_dept.id = t_user.dept_id"));
    }

    @Test
    @DisplayName("左连接与右连接片段同样参与参数顺序")
    void leftAndRightJoinFragmentsAreRendered() {
        JoinQuery sub = JoinQuery.from("t_log").where("level = ?", "ERROR");
        JoinQuery query = JoinQuery.from("t_user")
                .where("t_user.id = ?", 1L)
                .leftJoin("t_dept", "t_dept.id = t_user.dept_id")
                .rightJoin("t_org", "t_org.id = t_user.org_id")
                .subQueryJoin("l", sub, "l.user_id = t_user.id");

        assertEquals(List.of("ERROR", 1L), query.parameters());
        assertTrue(query.sql().contains("LEFT JOIN t_dept"));
        assertTrue(query.sql().contains("RIGHT JOIN t_org"));
    }
}
