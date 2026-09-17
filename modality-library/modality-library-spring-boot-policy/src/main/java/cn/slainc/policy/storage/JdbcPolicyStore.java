package cn.slainc.policy.storage;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.policy.model.Policy;
import cn.slainc.policy.model.PolicyDocument;
import cn.slainc.policy.model.PolicyStatus;
import cn.slainc.policy.model.PolicySubjectType;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class JdbcPolicyStore implements PolicyStore {

    private static final RowMapper<Policy> POLICY_ROW_MAPPER = JdbcPolicyStore::mapPolicy;

    private static final RowMapper<PolicyVersionRecord> VERSION_ROW_MAPPER = JdbcPolicyStore::mapVersion;

    private static final RowMapper<PolicyAuditRecord> AUDIT_ROW_MAPPER = JdbcPolicyStore::mapAudit;

    private final JdbcTemplate jdbcTemplate;

    private final String policyTable;

    private final String versionTable;

    private final String auditTable;

    private final boolean autoCreateTable;

    private volatile boolean ready;

    public JdbcPolicyStore(JdbcTemplate jdbcTemplate, String policyTable, String versionTable, String auditTable,
                           boolean autoCreateTable) {
        this.jdbcTemplate = jdbcTemplate;
        this.policyTable = policyTable;
        this.versionTable = versionTable;
        this.auditTable = auditTable;
        this.autoCreateTable = autoCreateTable;
    }

    @Override
    public Policy save(Policy policy) {
        ensureReady();
        long now = System.currentTimeMillis();
        String documentJson = policy.getDocument() == null ? null : policy.getDocument().toJson();
        if (exists(policy.getId())) {
            int updated = jdbcTemplate.update("UPDATE " + policyTable
                            + " SET name = ?, subject_type = ?, subject_id = ?, login_type = ?, status = ?,"
                            + " version = version + 1, document = ?, description = ?, updated_at = ?"
                            + " WHERE id = ? AND version = ?",
                    policy.getName(), policy.getSubjectType().code(), policy.getSubjectId(), policy.getLoginType(),
                    policy.getStatus().code(), documentJson, policy.getDescription(), now,
                    policy.getId(), policy.getVersion());
            if (updated == 0) {
                throw new BizException(ResultCode.CONFLICT,
                        "策略 " + policy.getId() + " 已被他人修改，请刷新后重试");
            }
            return find(policy.getId()).orElseThrow(() -> new BizException(ResultCode.DATA_NOT_FOUND,
                    "策略 " + policy.getId() + " 保存后读取失败"));
        }
        jdbcTemplate.update("INSERT INTO " + policyTable
                        + " (id, name, subject_type, subject_id, login_type, status, version, document, description, creator, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?)",
                policy.getId(), policy.getName(), policy.getSubjectType().code(), policy.getSubjectId(),
                policy.getLoginType(), policy.getStatus().code(), documentJson, policy.getDescription(),
                policy.getCreator(), now, now);
        return find(policy.getId()).orElse(policy);
    }

    @Override
    public Optional<Policy> find(String id) {
        ensureReady();
        try {
            return Optional.of(jdbcTemplate.queryForObject(
                    "SELECT * FROM " + policyTable + " WHERE id = ?", POLICY_ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<Policy> list() {
        ensureReady();
        return jdbcTemplate.query("SELECT * FROM " + policyTable + " ORDER BY created_at", POLICY_ROW_MAPPER);
    }

    @Override
    public List<Policy> listEnabled() {
        ensureReady();
        return jdbcTemplate.query("SELECT * FROM " + policyTable + " WHERE status = 'enabled' ORDER BY created_at",
                POLICY_ROW_MAPPER);
    }

    @Override
    public void delete(String id) {
        ensureReady();
        jdbcTemplate.update("DELETE FROM " + policyTable + " WHERE id = ?", id);
        jdbcTemplate.update("DELETE FROM " + versionTable + " WHERE policy_id = ?", id);
    }

    @Override
    public void archiveVersion(String policyId, int version, String document, String operator, int retainedVersions) {
        ensureReady();
        jdbcTemplate.update("DELETE FROM " + versionTable + " WHERE policy_id = ? AND version = ?", policyId, version);
        jdbcTemplate.update("INSERT INTO " + versionTable
                        + " (policy_id, version, document, operator, created_at) VALUES (?, ?, ?, ?, ?)",
                policyId, version, document, operator, System.currentTimeMillis());
        if (retainedVersions > 0) {
            jdbcTemplate.update("DELETE FROM " + versionTable
                    + " WHERE policy_id = ? AND version <= ?", policyId, version - retainedVersions);
        }
    }

    @Override
    public Optional<PolicyVersionRecord> findVersion(String policyId, int version) {
        ensureReady();
        try {
            return Optional.of(jdbcTemplate.queryForObject(
                    "SELECT * FROM " + versionTable + " WHERE policy_id = ? AND version = ?",
                    VERSION_ROW_MAPPER, policyId, version));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<PolicyVersionRecord> listVersions(String policyId) {
        ensureReady();
        return jdbcTemplate.query(
                "SELECT * FROM " + versionTable + " WHERE policy_id = ? ORDER BY version DESC", VERSION_ROW_MAPPER,
                policyId);
    }

    @Override
    public void appendAudit(PolicyAuditRecord record) {
        ensureReady();
        jdbcTemplate.update("INSERT INTO " + auditTable
                        + " (policy_id, operation, operator, before_content, after_content, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                record.getPolicyId(), record.getOperation(), record.getOperator(), record.getBeforeContent(),
                record.getAfterContent(), record.getCreatedAt());
    }

    @Override
    public List<PolicyAuditRecord> listAudit(String policyId, int limit) {
        ensureReady();
        int bound = limit <= 0 ? 100 : Math.min(limit, 1000);
        if (policyId == null) {
            return jdbcTemplate.query(
                    "SELECT * FROM " + auditTable + " ORDER BY created_at DESC, id DESC LIMIT " + bound,
                    AUDIT_ROW_MAPPER);
        }
        return jdbcTemplate.query(
                "SELECT * FROM " + auditTable + " WHERE policy_id = ? ORDER BY created_at DESC, id DESC LIMIT " + bound,
                AUDIT_ROW_MAPPER, policyId);
    }

    private boolean exists(String id) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + policyTable + " WHERE id = ?", Long.class, id);
        return count != null && count > 0;
    }

    private void ensureReady() {
        if (ready || !autoCreateTable) {
            return;
        }
        synchronized (this) {
            if (ready) {
                return;
            }
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + policyTable + " ("
                    + "id VARCHAR(64) NOT NULL PRIMARY KEY,"
                    + "name VARCHAR(128) NOT NULL,"
                    + "subject_type VARCHAR(16) NOT NULL,"
                    + "subject_id VARCHAR(64),"
                    + "login_type VARCHAR(16),"
                    + "status VARCHAR(16) NOT NULL DEFAULT 'enabled',"
                    + "version INT NOT NULL DEFAULT 1,"
                    + "document TEXT NOT NULL,"
                    + "description VARCHAR(256),"
                    + "creator VARCHAR(64),"
                    + "created_at BIGINT NOT NULL,"
                    + "updated_at BIGINT NOT NULL)");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + versionTable + " ("
                    + "policy_id VARCHAR(64) NOT NULL,"
                    + "version INT NOT NULL,"
                    + "document TEXT NOT NULL,"
                    + "operator VARCHAR(64),"
                    + "created_at BIGINT NOT NULL,"
                    + "PRIMARY KEY (policy_id, version))");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + auditTable + " ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + "policy_id VARCHAR(64) NOT NULL,"
                    + "operation VARCHAR(16) NOT NULL,"
                    + "operator VARCHAR(64),"
                    + "before_content TEXT,"
                    + "after_content TEXT,"
                    + "created_at BIGINT NOT NULL)");
            ready = true;
        }
    }

    private static Policy mapPolicy(ResultSet resultSet, int rowNumber) throws SQLException {
        Policy policy = new Policy();
        policy.setId(resultSet.getString("id"));
        policy.setName(resultSet.getString("name"));
        policy.setSubjectType(PolicySubjectType.of(resultSet.getString("subject_type")));
        policy.setSubjectId(resultSet.getString("subject_id"));
        policy.setLoginType(resultSet.getString("login_type"));
        policy.setStatus(PolicyStatus.of(resultSet.getString("status")));
        policy.setVersion(resultSet.getInt("version"));
        String document = resultSet.getString("document");
        policy.setDocument(document == null ? null : PolicyDocument.parse(document));
        policy.setDescription(resultSet.getString("description"));
        policy.setCreator(resultSet.getString("creator"));
        policy.setCreatedAt(resultSet.getLong("created_at"));
        policy.setUpdatedAt(resultSet.getLong("updated_at"));
        return policy;
    }

    private static PolicyVersionRecord mapVersion(ResultSet resultSet, int rowNumber) throws SQLException {
        PolicyVersionRecord record = new PolicyVersionRecord();
        record.setPolicyId(resultSet.getString("policy_id"));
        record.setVersion(resultSet.getInt("version"));
        record.setDocument(resultSet.getString("document"));
        record.setOperator(resultSet.getString("operator"));
        record.setCreatedAt(resultSet.getLong("created_at"));
        return record;
    }

    private static PolicyAuditRecord mapAudit(ResultSet resultSet, int rowNumber) throws SQLException {
        PolicyAuditRecord record = new PolicyAuditRecord();
        record.setPolicyId(resultSet.getString("policy_id"));
        record.setOperation(resultSet.getString("operation"));
        record.setOperator(resultSet.getString("operator"));
        record.setBeforeContent(resultSet.getString("before_content"));
        record.setAfterContent(resultSet.getString("after_content"));
        record.setCreatedAt(resultSet.getLong("created_at"));
        return record;
    }
}
