package cn.slainc.modules.upms.service;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.modules.upms.config.UpmsProperties;
import cn.slainc.modules.upms.entity.RoleRecord;
import cn.slainc.modules.upms.entity.UserLoginRecordRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.entity.UserRoleRecord;
import cn.slainc.modules.upms.mapper.RoleMapper;
import cn.slainc.modules.upms.mapper.UserLoginRecordMapper;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.modules.upms.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UserStatsService {

    private final UserMapper userMapper;

    private final UserRoleMapper userRoleMapper;

    private final RoleMapper roleMapper;

    private final UserLoginRecordMapper loginRecordMapper;

    private final UpmsProperties properties;

    public UserStatsService(UserMapper userMapper, UserRoleMapper userRoleMapper, RoleMapper roleMapper,
                            UserLoginRecordMapper loginRecordMapper, UpmsProperties properties) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.loginRecordMapper = loginRecordMapper;
        this.properties = properties;
    }

    public Map<String, Object> overview() {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalUsers", userMapper.selectCount(activeWrapper()));
        long dayStart = startOfDay(0);
        overview.put("dau", distinctActiveUsers(dayStart));
        long mauStart = startOfDay(-(properties.getStats().getMauDays() - 1));
        overview.put("mau", distinctActiveUsers(mauStart));
        return overview;
    }

    public List<Map<String, Object>> newUserTrend(int days) {
        int range = Math.max(1, days <= 0 ? properties.getStats().getTrendDays() : days);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int offset = -(range - 1); offset <= 0; offset++) {
            long from = startOfDay(offset);
            long to = startOfDay(offset + 1);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", LocalDate.now().plusDays(offset).toString());
            point.put("count", userMapper.selectCount(activeWrapper()
                    .ge(UserRecord::getCreateTime, from)
                    .lt(UserRecord::getCreateTime, to)));
            trend.add(point);
        }
        return trend;
    }

    public List<Map<String, Object>> roleDistribution() {
        List<RoleRecord> roles = roleMapper.selectList(null);
        Map<Long, String> roleNames = new LinkedHashMap<>();
        for (RoleRecord role : roles) {
            roleNames.put(role.getId(), role.getName());
        }
        Map<String, Integer> counter = new LinkedHashMap<>();
        int unassigned = 0;
        for (UserRecord user : userMapper.selectList(activeWrapper())) {
            List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleRecord>()
                            .eq(UserRoleRecord::getUserId, user.getId()))
                    .stream().map(UserRoleRecord::getRoleId).toList();
            if (roleIds.isEmpty()) {
                unassigned++;
                continue;
            }
            for (Long roleId : roleIds) {
                String name = roleNames.getOrDefault(roleId, "角色#" + roleId);
                counter.merge(name, 1, Integer::sum);
            }
        }
        List<Map<String, Object>> distribution = new ArrayList<>();
        for (var entry : counter.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("role", entry.getKey());
            row.put("count", entry.getValue());
            distribution.add(row);
        }
        if (unassigned > 0) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("role", "未分配角色");
            row.put("count", unassigned);
            distribution.add(row);
        }
        return distribution;
    }

    private LambdaQueryWrapper<UserRecord> activeWrapper() {
        // 平台超管看全量，租户侧由租户拦截器自动限定
        return new LambdaQueryWrapper<UserRecord>().eq(UserRecord::getDeleted, false);
    }

    private long distinctActiveUsers(long fromMillis) {
        List<UserLoginRecordRecord> records = loginRecordMapper.selectList(
                new LambdaQueryWrapper<UserLoginRecordRecord>()
                        .eq(UserLoginRecordRecord::getSucceeded, true)
                        .ge(UserLoginRecordRecord::getCreateTime, fromMillis)
                        .eq(!UserContext.isSuperAdmin(), UserLoginRecordRecord::getTenantId,
                                TenantContext.getOrDefault()));
        return records.stream().map(UserLoginRecordRecord::getUserId).filter(id -> id != null).distinct().count();
    }

    private long startOfDay(int dayOffset) {
        return LocalDate.now().plusDays(dayOffset).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
