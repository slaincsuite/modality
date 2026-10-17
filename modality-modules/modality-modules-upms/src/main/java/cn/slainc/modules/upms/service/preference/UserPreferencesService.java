package cn.slainc.modules.upms.service.preference;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.dto.UserPreferencesView;
import cn.slainc.modules.upms.entity.UserPreferencesRecord;
import cn.slainc.modules.upms.mapper.UserPreferencesMapper;
import cn.slainc.utils.json.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class UserPreferencesService {

    private static final Pattern SCENE_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private static final int MAX_SETTINGS_LENGTH = 65535;

    private final UserPreferencesMapper preferencesMapper;

    public UserPreferencesService(UserPreferencesMapper preferencesMapper) {
        this.preferencesMapper = preferencesMapper;
    }

    public UserPreferencesView get(String scene) {
        Long userId = currentUserId();
        String safeScene = scene(scene);
        UserPreferencesRecord record = preferencesMapper.selectOne(new LambdaQueryWrapper<UserPreferencesRecord>()
                .eq(UserPreferencesRecord::getUserId, userId)
                .eq(UserPreferencesRecord::getScene, safeScene)
                .last("LIMIT 1"));
        if (record == null) {
            return new UserPreferencesView(safeScene, new LinkedHashMap<>(), null);
        }
        return new UserPreferencesView(record.getScene(), JsonUtils.parseMap(record.getSettingsJson()),
                record.getUpdateTime());
    }

    @Transactional
    public UserPreferencesView update(String scene, Map<String, Object> settings) {
        Long userId = currentUserId();
        String safeScene = scene(scene);
        if (settings == null) {
            throw new BizException("偏好设置内容不能为空");
        }
        String json = JsonUtils.toJson(settings);
        if (json == null || json.length() > MAX_SETTINGS_LENGTH) {
            throw new BizException("偏好设置内容过大，最多允许 65535 个字符");
        }
        long now = System.currentTimeMillis();
        UserPreferencesRecord record = preferencesMapper.selectOne(new LambdaQueryWrapper<UserPreferencesRecord>()
                .eq(UserPreferencesRecord::getUserId, userId)
                .eq(UserPreferencesRecord::getScene, safeScene)
                .last("LIMIT 1"));
        if (record == null) {
            record = new UserPreferencesRecord();
            record.setTenantId(TenantContext.getOrDefault());
            record.setUserId(userId);
            record.setScene(safeScene);
            record.setSettingsJson(json);
            record.setCreateTime(now);
            record.setUpdateTime(now);
            preferencesMapper.insert(record);
        } else {
            record.setSettingsJson(json);
            record.setUpdateTime(now);
            preferencesMapper.updateById(record);
        }
        return new UserPreferencesView(safeScene, JsonUtils.parseMap(json), record.getUpdateTime());
    }

    private Long currentUserId() {
        Long userId = UserContext.userId();
        if (userId == null) {
            throw new BizException("登录状态已失效，请重新登录");
        }
        return userId;
    }

    private String scene(String value) {
        if (value == null || !SCENE_PATTERN.matcher(value).matches()) {
            throw new BizException("偏好场景只允许 1 到 64 位字母、数字、点、下划线或短横线");
        }
        return value;
    }
}
