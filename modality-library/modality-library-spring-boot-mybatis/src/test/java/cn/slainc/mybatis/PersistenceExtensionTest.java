package cn.slainc.mybatis;

import cn.slainc.core.result.PageResult;
import cn.slainc.mybatis.join.JoinQuery;
import cn.slainc.mybatis.join.JoinTemplate;
import cn.slainc.mybatis.page.PageQuery;
import cn.slainc.mybatis.procedure.StoredProcedureCall;
import cn.slainc.mybatis.procedure.StoredProcedureExecutor;
import cn.slainc.mybatis.procedure.StoredProcedureResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = MybatisTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PersistenceExtensionTest {

    private static final String FIND_ORDERS_ALIAS = """
            CREATE ALIAS IF NOT EXISTS FIND_ORDERS AS $$
            java.sql.ResultSet findOrders(java.sql.Connection conn, long minId) throws java.sql.SQLException {
                return conn.createStatement().executeQuery("SELECT id, status FROM t_order WHERE id >= " + minId + " ORDER BY id");
            }
            $$""";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JoinTemplate joinTemplate;

    @Autowired
    private StoredProcedureExecutor procedureExecutor;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS t_order (id BIGINT PRIMARY KEY, user_id BIGINT,"
                + " amount DECIMAL(12,2), status VARCHAR(32))");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS t_order_item (id BIGINT PRIMARY KEY, order_id BIGINT,"
                + " product VARCHAR(64))");
        jdbcTemplate.execute("DELETE FROM t_order");
        jdbcTemplate.execute("DELETE FROM t_order_item");
        jdbcTemplate.update("INSERT INTO t_order VALUES (1, 100, 10.50, 'PAID')");
        jdbcTemplate.update("INSERT INTO t_order VALUES (2, 100, 20.00, 'PAID')");
        jdbcTemplate.update("INSERT INTO t_order VALUES (3, 200, 30.00, 'SHIPPED')");
        jdbcTemplate.update("INSERT INTO t_order_item VALUES (1, 1, '苹果')");
        jdbcTemplate.update("INSERT INTO t_order_item VALUES (2, 1, '香蕉')");
        jdbcTemplate.update("INSERT INTO t_order_item VALUES (3, 2, '橙子')");
    }

    @Test
    @DisplayName("多表关联查询返回关联列")
    void joinQueryReturnsJoinedColumns() {
        JoinQuery query = JoinQuery.from("t_order o")
                .select("o.id", "o.user_id", "o.status", "i.product")
                .leftJoin("t_order_item i", "i.order_id = o.id")
                .where("o.user_id = ?", 100L)
                .orderBy("o.id", "i.id");

        List<Map<String, Object>> rows = joinTemplate.selectMaps(query);
        assertEquals(3, rows.size());
        assertTrue(hasColumn(rows.get(0), "user_id"), "实际列: " + rows.get(0).keySet());
        assertEquals("苹果", rows.get(0).get("PRODUCT") == null ? rows.get(0).get("product") : rows.get(0).get("PRODUCT"));
    }

    @Test
    @DisplayName("关联查询映射为 Bean 并支持分页")
    void joinQueryMapsToBeanAndPaginates() {
        List<OrderRow> orders = joinTemplate.selectList(JoinQuery.from("t_order o")
                .select("o.id", "o.user_id", "o.amount", "o.status")
                .where("o.user_id = ?", 100L)
                .orderBy("o.id"), OrderRow.class);
        assertEquals(2, orders.size());
        assertEquals("PAID", orders.get(0).getStatus());
        assertEquals(100L, orders.get(0).getUserId());

        PageResult<OrderRow> first = joinTemplate.selectPage(JoinQuery.from("t_order o")
                .select("o.id", "o.status").where("o.status = ?", "PAID").orderBy("o.id"),
                OrderRow.class, new PageQuery(1L, 1L));
        assertEquals(2L, first.total());
        assertEquals(1, first.records().size());
        assertEquals(1L, first.records().get(0).getId());

        PageResult<OrderRow> second = joinTemplate.selectPage(JoinQuery.from("t_order o")
                .select("o.id", "o.status").where("o.status = ?", "PAID").orderBy("o.id"),
                OrderRow.class, new PageQuery(2L, 1L));
        assertEquals(1, second.records().size());
        assertEquals(2L, second.records().get(0).getId());
    }

    @Test
    @DisplayName("子查询可作为 IN 条件")
    void subQueryWorks() {
        JoinQuery sub = JoinQuery.from("t_order_item i").select("i.order_id").where("i.product = ?", "橙子");
        List<Map<String, Object>> rows = joinTemplate.selectMaps(JoinQuery.from("t_order o")
                .select("o.id", "o.status")
                .subQueryIn("o.id", sub));
        assertEquals(1, rows.size());
        assertEquals(2L, ((Number) rows.get(0).get("ID")).longValue());
    }

    @Test
    @DisplayName("一对多结果集按父键分组装配")
    void groupChildrenAssembles() {
        List<OrderRow> orders = joinTemplate.selectList(JoinQuery.from("t_order o")
                .select("o.id", "o.user_id", "o.status")
                .where("o.user_id = ?", 100L)
                .orderBy("o.id"), OrderRow.class);
        List<ItemRow> items = jdbcTemplate.query("SELECT id, order_id, product FROM t_order_item",
                (rs, rowNum) -> {
                    ItemRow item = new ItemRow();
                    item.setId(rs.getLong("id"));
                    item.setOrderId(rs.getLong("order_id"));
                    item.setProduct(rs.getString("product"));
                    return item;
                });

        joinTemplate.groupChildren(orders, OrderRow::getId, items, ItemRow::getOrderId, OrderRow::setItems);

        assertEquals(2, orders.get(0).getItems().size());
        assertEquals("苹果", orders.get(0).getItems().get(0).getProduct());
        assertEquals(1, orders.get(1).getItems().size());
    }

    @Test
    @DisplayName("存储过程返回结果集并接受 IN 参数")
    void storedProcedureReturnsResultSet() {
        jdbcTemplate.execute(FIND_ORDERS_ALIAS);

        StoredProcedureResult result = procedureExecutor.call(StoredProcedureCall.of("FIND_ORDERS")
                .in("minId", 2L));

        assertEquals(1, result.getResultSets().size());
        List<Map<String, Object>> rows = result.firstResultSet();
        assertEquals(2, rows.size());
        assertEquals(2L, ((Number) rows.get(0).get("ID")).longValue());
        assertEquals(3L, ((Number) rows.get(1).get("ID")).longValue());
    }

    private static boolean hasColumn(Map<String, Object> row, String column) {
        return row.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(column));
    }

    public static class OrderRow {

        private Long id;

        private Long userId;

        private BigDecimal amount;

        private String status;

        private List<ItemRow> items;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public List<ItemRow> getItems() {
            return items;
        }

        public void setItems(List<ItemRow> items) {
            this.items = items;
        }
    }

    public static class ItemRow {

        private Long id;

        private Long orderId;

        private String product;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getOrderId() {
            return orderId;
        }

        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }

        public String getProduct() {
            return product;
        }

        public void setProduct(String product) {
            this.product = product;
        }
    }
}