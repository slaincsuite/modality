package cn.slainc.modules.policy.store;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.policy.PolicyCenterKeys;
import cn.slainc.modules.policy.entity.PolicyCustomTemplateRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

public class PolicyTemplateStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;

    public PolicyTemplateStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PolicyCustomTemplateRecord insert(String name, String description, String document, String creator) {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        String id = "tpl_" + HexFormat.of().formatHex(bytes);
        long now = System.currentTimeMillis();
        try {
            jdbcTemplate.update("INSERT INTO " + PolicyCenterKeys.POLICY_TEMPLATE_TABLE
                            + " (id, name, description, document, creator, create_time, update_time)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                    id, name, description, document, creator, now, now);
        } catch (DuplicateKeyException exception) {
            throw new BizException(ResultCode.DATA_DUPLICATE, "自定义模板名称已存在: " + name);
        }
        return new PolicyCustomTemplateRecord(id, name, description, document, creator, now, now);
    }

    public PolicyCustomTemplateRecord update(String id, String name, String description, String document) {
        PolicyCustomTemplateRecord existing = require(id);
        long now = System.currentTimeMillis();
        try {
            jdbcTemplate.update("UPDATE " + PolicyCenterKeys.POLICY_TEMPLATE_TABLE
                            + " SET name = ?, description = ?, document = ?, update_time = ? WHERE id = ?",
                    name, description, document, now, id);
        } catch (DuplicateKeyException exception) {
            throw new BizException(ResultCode.DATA_DUPLICATE, "自定义模板名称已存在: " + name);
        }
        return new PolicyCustomTemplateRecord(id, name, description, document, existing.creator(),
                existing.createTime(), now);
    }

    public void delete(String id) {
        require(id);
        jdbcTemplate.update("DELETE FROM " + PolicyCenterKeys.POLICY_TEMPLATE_TABLE + " WHERE id = ?", id);
    }

    public Optional<PolicyCustomTemplateRecord> find(String id) {
        return jdbcTemplate.query("SELECT * FROM " + PolicyCenterKeys.POLICY_TEMPLATE_TABLE + " WHERE id = ?",
                PolicyTemplateStore::mapRow, id).stream().findFirst();
    }

    public List<PolicyCustomTemplateRecord> list() {
        return jdbcTemplate.query("SELECT * FROM " + PolicyCenterKeys.POLICY_TEMPLATE_TABLE
                + " ORDER BY update_time DESC", PolicyTemplateStore::mapRow);
    }

    private PolicyCustomTemplateRecord require(String id) {
        return find(id).orElseThrow(() -> new BizException(ResultCode.DATA_NOT_FOUND,
                "自定义模板 " + id + " 不存在"));
    }

    private static PolicyCustomTemplateRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PolicyCustomTemplateRecord(rs.getString("id"), rs.getString("name"),
                rs.getString("description"), rs.getString("document"), rs.getString("creator"),
                rs.getLong("create_time"), rs.getLong("update_time"));
    }
}
