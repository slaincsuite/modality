package cn.slainc.modules.upms.service.registry;

import cn.slainc.modules.upms.service.access.AccessControlService;

import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.UpmsKeys;
import cn.slainc.modules.upms.entity.SystemRegistryRecord;
import cn.slainc.modules.upms.mapper.SystemRegistryMapper;
import cn.slainc.modules.upms.security.UpmsPermissionCacheInvalidator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SystemRegistryService {

    private static final Logger log = LoggerFactory.getLogger(SystemRegistryService.class);

    private final SystemRegistryMapper systemMapper;

    private final AccessControlService accessControlService;

    private final UpmsPermissionCacheInvalidator cacheInvalidator;

    public SystemRegistryService(SystemRegistryMapper systemMapper, AccessControlService accessControlService,
                                 UpmsPermissionCacheInvalidator cacheInvalidator) {
        this.systemMapper = systemMapper;
        this.accessControlService = accessControlService;
        this.cacheInvalidator = cacheInvalidator;
    }

    public SystemRegistryRecord create(String code, String name, String moduleCode, String visibility,
                                       String groupName, String icon, String url, Integer sort) {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new BizException("系统编码与名称不能为空");
        }
        if (moduleCode == null || moduleCode.isBlank()) {
            throw new BizException("系统所属业务模块编码不能为空，访问权限码由模块编码生成");
        }
        String visible = visibility == null || visibility.isBlank()
                ? SystemRegistryRecord.VISIBILITY_PUBLIC : visibility;
        if (!SystemRegistryRecord.VISIBILITY_PUBLIC.equals(visible)
                && !SystemRegistryRecord.VISIBILITY_PRIVATE.equals(visible)) {
            throw new BizException("非法的系统可见性: " + visibility + "，只允许 PUBLIC、PRIVATE");
        }
        long now = System.currentTimeMillis();
        SystemRegistryRecord system = new SystemRegistryRecord();
        system.setCode(code.trim());
        system.setName(name.trim());
        system.setModuleCode(moduleCode.trim());
        system.setVisibility(visible);
        system.setGroupName(groupName);
        system.setIcon(icon);
        system.setUrl(url);
        system.setSort(sort == null ? 0 : sort);
        system.setStatus(UpmsKeys.STATUS_ENABLED);
        system.setCreateTime(now);
        system.setUpdateTime(now);
        try {
            systemMapper.insert(system);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException("系统编码 " + code + " 已存在");
        }
        return system;
    }

    public SystemRegistryRecord update(Long id, String name, String groupName, String icon, String url,
                                       Integer sort, String status) {
        SystemRegistryRecord system = require(id);
        if (name != null && !name.isBlank()) {
            system.setName(name.trim());
        }
        if (groupName != null) {
            system.setGroupName(groupName);
        }
        if (icon != null) {
            system.setIcon(icon);
        }
        if (url != null) {
            system.setUrl(url);
        }
        if (sort != null) {
            system.setSort(sort);
        }
        if (status != null) {
            if (!UpmsKeys.STATUS_ENABLED.equals(status) && !UpmsKeys.STATUS_DISABLED.equals(status)) {
                throw new BizException("系统状态只允许 enabled、disabled");
            }
            system.setStatus(status);
        }
        system.setUpdateTime(System.currentTimeMillis());
        systemMapper.updateById(system);
        cacheInvalidator.invalidateUsers(accessControlService.userIdsOfSystem(id));
        return system;
    }

    // 可见性变更即时生效：失效已有授权记录的用户；其余用户的 PUBLIC 码最迟一个权限缓存 TTL 失效
    public SystemRegistryRecord changeVisibility(Long id, String visibility) {
        if (!SystemRegistryRecord.VISIBILITY_PUBLIC.equals(visibility)
                && !SystemRegistryRecord.VISIBILITY_PRIVATE.equals(visibility)) {
            throw new BizException("非法的系统可见性: " + visibility + "，只允许 PUBLIC、PRIVATE");
        }
        SystemRegistryRecord system = require(id);
        if (system.getVisibility().equals(visibility)) {
            return system;
        }
        system.setVisibility(visibility);
        system.setUpdateTime(System.currentTimeMillis());
        systemMapper.updateById(system);
        cacheInvalidator.invalidateUsers(accessControlService.userIdsOfSystem(id));
        if (SystemRegistryRecord.VISIBILITY_PRIVATE.equals(visibility)) {
            log.info("系统 {} 由 PUBLIC 转为 PRIVATE，未持授权用户的访问权限码最迟一个权限缓存 TTL 后失效",
                    system.getCode());
        }
        return system;
    }

    public void delete(Long id) {
        require(id);
        long grants = accessControlService.countBySystem(id);
        if (grants > 0) {
            throw new BizException("该系统仍有 " + grants + " 条授权记录，先撤销授权后再删除");
        }
        systemMapper.deleteById(id);
    }

    public List<SystemRegistryRecord> list(String groupName, String status) {
        return systemMapper.selectList(new LambdaQueryWrapper<SystemRegistryRecord>()
                .eq(groupName != null && !groupName.isBlank(), SystemRegistryRecord::getGroupName, groupName)
                .eq(status != null && !status.isBlank(), SystemRegistryRecord::getStatus, status)
                .orderByAsc(SystemRegistryRecord::getSort).orderByAsc(SystemRegistryRecord::getId));
    }

    public SystemRegistryRecord require(Long id) {
        SystemRegistryRecord system = systemMapper.selectById(id);
        if (system == null) {
            throw new BizException("系统不存在: " + id);
        }
        return system;
    }

    public SystemRegistryRecord byCode(String code) {
        return systemMapper.selectOne(new LambdaQueryWrapper<SystemRegistryRecord>()
                .eq(SystemRegistryRecord::getCode, code).last("LIMIT 1"));
    }

    public List<SystemRegistryRecord> enabledPublicSystems() {
        return systemMapper.selectList(new LambdaQueryWrapper<SystemRegistryRecord>()
                .eq(SystemRegistryRecord::getStatus, UpmsKeys.STATUS_ENABLED)
                .eq(SystemRegistryRecord::getVisibility, SystemRegistryRecord.VISIBILITY_PUBLIC));
    }

    public List<SystemRegistryRecord> enabledPrivateSystems() {
        return systemMapper.selectList(new LambdaQueryWrapper<SystemRegistryRecord>()
                .eq(SystemRegistryRecord::getStatus, UpmsKeys.STATUS_ENABLED)
                .eq(SystemRegistryRecord::getVisibility, SystemRegistryRecord.VISIBILITY_PRIVATE));
    }
}
