package cn.slainc.modules.upms.log;

import cn.slainc.modules.upms.entity.UserLoginRecordRecord;
import cn.slainc.modules.upms.mapper.UserLoginRecordMapper;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.core.result.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LoginRecordService {

    private final UserLoginRecordMapper loginRecordMapper;

    public LoginRecordService(UserLoginRecordMapper loginRecordMapper) {
        this.loginRecordMapper = loginRecordMapper;
    }

    public PageResult<UserLoginRecordRecord> search(Long userId, String username, String loginType, String clientIp,
                                                    Boolean succeeded, Long from, Long to, long current, long size) {
        Page<UserLoginRecordRecord> page = loginRecordMapper.selectPage(Page.of(current, size),
                baseWrapper(userId, username, loginType, clientIp, succeeded, from, to)
                        .orderByDesc(UserLoginRecordRecord::getId));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public List<UserLoginRecordRecord> list(Long userId, String username, String loginType, String clientIp,
                                            Boolean succeeded, Long from, Long to, int limit) {
        return loginRecordMapper.selectList(baseWrapper(userId, username, loginType, clientIp, succeeded, from, to)
                .orderByDesc(UserLoginRecordRecord::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }

    public List<Map<String, Object>> topIps(int limit) {
        return topGroupBy(UserLoginRecordRecord::getClientIp, "clientIp", limit);
    }

    public List<Map<String, Object>> topDevices(int limit) {
        return topGroupBy(UserLoginRecordRecord::getDevice, "device", limit);
    }

    private LambdaQueryWrapper<UserLoginRecordRecord> baseWrapper(Long userId, String username, String loginType,
                                                                  String clientIp, Boolean succeeded, Long from,
                                                                  Long to) {
        return new LambdaQueryWrapper<UserLoginRecordRecord>()
                .eq(!UserContext.isSuperAdmin(), UserLoginRecordRecord::getTenantId, TenantContext.getOrDefault())
                .eq(userId != null, UserLoginRecordRecord::getUserId, userId)
                .like(username != null && !username.isBlank(), UserLoginRecordRecord::getUsername, username)
                .eq(loginType != null && !loginType.isBlank(), UserLoginRecordRecord::getLoginType, loginType)
                .eq(clientIp != null && !clientIp.isBlank(), UserLoginRecordRecord::getClientIp, clientIp)
                .eq(succeeded != null, UserLoginRecordRecord::getSucceeded, succeeded)
                .ge(from != null, UserLoginRecordRecord::getCreateTime, from)
                .le(to != null, UserLoginRecordRecord::getCreateTime, to);
    }

    private List<Map<String, Object>> topGroupBy(
            SFunction<UserLoginRecordRecord, ?> column, String label, int limit) {
        List<UserLoginRecordRecord> records = loginRecordMapper.selectList(
                new LambdaQueryWrapper<UserLoginRecordRecord>()
                        .eq(!UserContext.isSuperAdmin(), UserLoginRecordRecord::getTenantId,
                                TenantContext.getOrDefault()));
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (UserLoginRecordRecord record : records) {
            Object value = column.apply(record);
            String key = value == null ? null : String.valueOf(value);
            if (key != null && !key.isBlank()) {
                counter.merge(key, 1, Integer::sum);
            }
        }
        return counter.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(Math.max(1, limit))
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put(label, entry.getKey());
                    row.put("count", entry.getValue());
                    return row;
                })
                .toList();
    }
}
