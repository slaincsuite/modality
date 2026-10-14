package cn.slainc.modules.upms.service.user;

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
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

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
        Map<Long, String> roleNames = new LinkedHashMap<>();
        for (RoleRecord role : roleMapper.selectList(null)) {
            roleNames.put(role.getId(), role.getName());
        }
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (Map<String, Object> row : userRoleMapper.selectMaps(new QueryWrapper<UserRoleRecord>()
                .select("role_id", "COUNT(*) AS binding_count")
                .groupBy("role_id"))) {
            Object roleId = column(row, "role_id");
            Object bindingCount = column(row, "binding_count");
            if (roleId == null || bindingCount == null) {
                continue;
            }
            long id = ((Number) roleId).longValue();
            String name = roleNames.getOrDefault(id, "角色#" + id);
            counter.merge(name, (int) ((Number) bindingCount).longValue(), Integer::sum);
        }
        List<Map<String, Object>> distribution = new ArrayList<>();
        for (var entry : counter.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("role", entry.getKey());
            row.put("count", entry.getValue());
            distribution.add(row);
        }
        long total = userMapper.selectCount(activeWrapper());
        long assigned = countUsersWithRole();
        long unassigned = Math.max(0L, total - assigned);
        if (unassigned > 0) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("role", "未分配角色");
            row.put("count", (int) unassigned);
            distribution.add(row);
        }
        return distribution;
    }

    private LambdaQueryWrapper<UserRecord> activeWrapper() {
        // 平台超管看全量，租户侧由租户拦截器自动限定
        return new LambdaQueryWrapper<UserRecord>().eq(UserRecord::getDeleted, false);
    }

    private long distinctActiveUsers(long fromMillis) {
        List<Object> counts = loginRecordMapper.selectObjs(new QueryWrapper<UserLoginRecordRecord>()
                .select("COUNT(DISTINCT user_id)")
                .eq("succeeded", true)
                .ge("create_time", fromMillis)
                .eq(!UserContext.isSuperAdmin(), "tenant_id", TenantContext.getOrDefault()));
        return counts.isEmpty() || counts.get(0) == null ? 0L : ((Number) counts.get(0)).longValue();
    }

    private long countUsersWithRole() {
        List<Object> counts = userRoleMapper.selectObjs(new QueryWrapper<UserRoleRecord>()
                .select("COUNT(DISTINCT user_id)"));
        return counts.isEmpty() || counts.get(0) == null ? 0L : ((Number) counts.get(0)).longValue();
    }

    // H2 与 MySQL 对未加引号别名的返回大小写不一致，按列名忽略大小写取值
    private static Object column(Map<String, Object> row, String name) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private long startOfDay(int dayOffset) {
        return LocalDate.now().plusDays(dayOffset).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
