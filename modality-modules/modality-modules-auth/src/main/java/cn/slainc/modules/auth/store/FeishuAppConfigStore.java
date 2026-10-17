package cn.slainc.modules.auth.store;

import cn.slainc.modules.auth.AuthKeys;
import cn.slainc.modules.auth.third.FeishuAppConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

public class FeishuAppConfigStore {

    private static final String TABLE_NAME = AuthKeys.FEISHU_APP_CONFIG_TABLE;

    private static final RowMapper<FeishuAppConfig> MAPPER = (rs, rowNum) -> {
        FeishuAppConfig config = new FeishuAppConfig();
        config.setId(rs.getLong("id"));
        config.setPlatform(rs.getString("platform"));
        config.setAppId(rs.getString("app_id"));
        config.setAppSecretCipher(rs.getString("app_secret_cipher"));
        config.setVerificationTokenCipher(rs.getString("verification_token_cipher"));
        config.setEncryptKeyCipher(rs.getString("encrypt_key_cipher"));
        config.setCallbackUrl(rs.getString("callback_url"));
        config.setEnabled(rs.getBoolean("enabled"));
        config.setCreateTime(rs.getLong("create_time"));
        config.setUpdateTime(rs.getLong("update_time"));
        return config;
    };

    private final JdbcTemplate jdbcTemplate;

    public FeishuAppConfigStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public FeishuAppConfig find(String platform) {
        List<FeishuAppConfig> configs = jdbcTemplate.query("SELECT * FROM " + TABLE_NAME + " WHERE platform = ?",
                MAPPER, platform);
        return configs.isEmpty() ? null : configs.get(0);
    }

    public FeishuAppConfig save(FeishuAppConfig config) {
        long now = System.currentTimeMillis();
        if (config.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("INSERT INTO " + TABLE_NAME
                                + " (platform, app_id, app_secret_cipher, verification_token_cipher, encrypt_key_cipher,"
                                + " callback_url, enabled, create_time, update_time) VALUES (?,?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, config.getPlatform());
                statement.setString(2, config.getAppId());
                statement.setString(3, config.getAppSecretCipher());
                statement.setString(4, config.getVerificationTokenCipher());
                statement.setString(5, config.getEncryptKeyCipher());
                statement.setString(6, config.getCallbackUrl());
                statement.setBoolean(7, Boolean.TRUE.equals(config.getEnabled()));
                statement.setLong(8, config.getCreateTime() == null ? now : config.getCreateTime());
                statement.setLong(9, now);
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key != null) {
                config.setId(key.longValue());
            }
        } else {
            jdbcTemplate.update("UPDATE " + TABLE_NAME
                            + " SET app_id = ?, app_secret_cipher = ?, verification_token_cipher = ?,"
                            + " encrypt_key_cipher = ?, callback_url = ?, enabled = ?, update_time = ? WHERE id = ?",
                    config.getAppId(), config.getAppSecretCipher(), config.getVerificationTokenCipher(),
                    config.getEncryptKeyCipher(), config.getCallbackUrl(), Boolean.TRUE.equals(config.getEnabled()),
                    now, config.getId());
        }
        config.setUpdateTime(now);
        return config;
    }
}
