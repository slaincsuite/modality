package cn.slainc.modules.upms.web.self;

import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.upms.dto.UserPreferencesView;
import cn.slainc.modules.upms.service.preference.UserPreferencesService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "通用列表偏好", description = "当前账号按业务场景保存列表偏好")
@RestController
@RequestMapping("/upms/preferences")
public class UserPreferencesController {

    private final UserPreferencesService preferencesService;

    public UserPreferencesController(UserPreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    @GetMapping("/{scene}")
    public UserPreferencesView get(@PathVariable String scene) {
        return preferencesService.get(scene);
    }

    @PutMapping("/{scene}")
    @AuditLog(value = "保存列表偏好", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "upms")
    public UserPreferencesView update(@PathVariable String scene, @RequestBody Map<String, Object> settings) {
        return preferencesService.update(scene, settings);
    }
}
