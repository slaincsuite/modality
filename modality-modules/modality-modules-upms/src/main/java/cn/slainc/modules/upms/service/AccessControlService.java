package cn.slainc.modules.upms.service;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.entity.AccessControlRecord;
import cn.slainc.modules.upms.entity.SystemRegistryRecord;
import cn.slainc.modules.upms.mapper.AccessControlMapper;
import cn.slainc.modules.upms.mapper.SystemRegistryMapper;
import cn.slainc.modules.upms.security.UpmsPermissionCacheInvalidator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// 直接依赖系统注册表 Mapper 而非 SystemRegistryService，避免与可见性变更逻辑互相持有形成环
public class AccessControlService {

    private final AccessControlMapper accessControlMapper;

    private final SystemRegistryMapper systemRegistryMapper;

    private final UpmsPermissionCacheInvalidator cacheInvalidator;

    public AccessControlService(AccessControlMapper accessControlMapper, SystemRegistryMapper systemRegistryMapper,
                                UpmsPermissionCacheInvalidator cacheInvalidator) {
        this.accessControlMapper = accessControlMapper;
        this.systemRegistryMapper = systemRegistryMapper;
        this.cacheInvalidator = cacheInvalidator;
    }

    @Transactional
    public List<AccessControlRecord> grant(Long systemId, List<Long> userIds, String operator) {
        SystemRegistryRecord system = requireSystem(systemId);
        if (SystemRegistryRecord.VISIBILITY_PUBLIC.equals(system.getVisibility())) {
            throw new BizException("系统 " + system.getCode() + " 是 PUBLIC，全员可见，无需单独授权");
        }
        List<AccessControlRecord> granted = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Long userId : userIds == null ? List.<Long>of() : userIds) {
            AccessControlRecord existing = accessControlMapper.selectOne(
                    new LambdaQueryWrapper<AccessControlRecord>()
                            .eq(AccessControlRecord::getSystemId, systemId)
                            .eq(AccessControlRecord::getUserId, userId).last("LIMIT 1"));
            if (existing != null) {
                if (existing.granted()) {
                    continue;
                }
                existing.setStatus(AccessControlRecord.STATUS_GRANTED);
                existing.setGrantedBy(operator);
                existing.setGrantedAt(now);
                accessControlMapper.updateById(existing);
                granted.add(existing);
                continue;
            }
            AccessControlRecord record = new AccessControlRecord();
            record.setTenantId(TenantContext.getOrDefault());
            record.setSystemId(systemId);
            record.setUserId(userId);
            record.setGrantedBy(operator);
            record.setGrantedAt(now);
            record.setStatus(AccessControlRecord.STATUS_GRANTED);
            accessControlMapper.insert(record);
            granted.add(record);
        }
        cacheInvalidator.invalidateUsers(userIds);
        return granted;
    }

    public void revoke(Long systemId, Long userId) {
        AccessControlRecord record = accessControlMapper.selectOne(
                new LambdaQueryWrapper<AccessControlRecord>()
                        .eq(AccessControlRecord::getSystemId, systemId)
                        .eq(AccessControlRecord::getUserId, userId).last("LIMIT 1"));
        if (record == null) {
            throw new BizException("用户 " + userId + " 在该系统下没有授权记录");
        }
        accessControlMapper.deleteById(record.getId());
        cacheInvalidator.invalidateUsers(List.of(userId));
    }

    @Transactional
    public int revokeBySystem(Long systemId) {
        List<AccessControlRecord> records = accessControlMapper.selectList(
                new LambdaQueryWrapper<AccessControlRecord>().eq(AccessControlRecord::getSystemId, systemId));
        for (AccessControlRecord record : records) {
            accessControlMapper.deleteById(record.getId());
        }
        if (!records.isEmpty()) {
            cacheInvalidator.invalidateUsers(records.stream().map(AccessControlRecord::getUserId).toList());
        }
        return records.size();
    }

    public List<AccessControlRecord> query(Long systemId, Long userId) {
        return accessControlMapper.selectList(new LambdaQueryWrapper<AccessControlRecord>()
                .eq(systemId != null, AccessControlRecord::getSystemId, systemId)
                .eq(userId != null, AccessControlRecord::getUserId, userId)
                .eq(AccessControlRecord::getStatus, AccessControlRecord.STATUS_GRANTED)
                .orderByDesc(AccessControlRecord::getId));
    }

    public List<Long> userIdsOfSystem(Long systemId) {
        return query(systemId, null).stream().map(AccessControlRecord::getUserId).toList();
    }

    public long countBySystem(Long systemId) {
        return accessControlMapper.selectCount(new LambdaQueryWrapper<AccessControlRecord>()
                .eq(AccessControlRecord::getSystemId, systemId));
    }

    // 用户可见的 PRIVATE 系统授权码集合，由权限提供者在鉴权链路调用
    public List<String> grantedAccessCodes(Long userId) {
        List<AccessControlRecord> grants = query(null, userId);
        if (grants.isEmpty()) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        for (AccessControlRecord grant : grants) {
            SystemRegistryRecord system = systemRegistryMapper.selectById(grant.getSystemId());
            if (system != null && system.enabled()
                    && SystemRegistryRecord.VISIBILITY_PRIVATE.equals(system.getVisibility())) {
                codes.add(system.accessPermissionCode());
            }
        }
        return codes;
    }

    private SystemRegistryRecord requireSystem(Long systemId) {
        SystemRegistryRecord system = systemRegistryMapper.selectById(systemId);
        if (system == null) {
            throw new BizException("系统不存在: " + systemId);
        }
        return system;
    }
}
