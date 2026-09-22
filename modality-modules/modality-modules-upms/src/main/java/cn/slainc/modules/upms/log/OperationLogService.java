package cn.slainc.modules.upms.log;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.core.result.PageResult;
import cn.slainc.modules.upms.entity.UserOperationLogRecord;
import cn.slainc.modules.upms.mapper.UserOperationLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

public class OperationLogService {

    private final UserOperationLogMapper operationLogMapper;

    public OperationLogService(UserOperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    public PageResult<UserOperationLogRecord> search(Long userId, String username, String module, String operation,
                                                     Boolean success, Long from, Long to, long current, long size) {
        Page<UserOperationLogRecord> page = operationLogMapper.selectPage(Page.of(current, size),
                baseWrapper(userId, username, module, operation, success, from, to)
                        .orderByDesc(UserOperationLogRecord::getId));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public List<UserOperationLogRecord> list(Long userId, String username, String module, String operation,
                                             Boolean success, Long from, Long to, int limit) {
        return operationLogMapper.selectList(baseWrapper(userId, username, module, operation, success, from, to)
                .orderByDesc(UserOperationLogRecord::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }

    private LambdaQueryWrapper<UserOperationLogRecord> baseWrapper(Long userId, String username, String module,
                                                                   String operation, Boolean success, Long from,
                                                                   Long to) {
        return new LambdaQueryWrapper<UserOperationLogRecord>()
                .eq(!UserContext.isSuperAdmin(), UserOperationLogRecord::getTenantId, TenantContext.getOrDefault())
                .eq(userId != null, UserOperationLogRecord::getUserId, userId)
                .like(username != null && !username.isBlank(), UserOperationLogRecord::getUsername, username)
                .eq(module != null && !module.isBlank(), UserOperationLogRecord::getModuleName, module)
                .eq(operation != null && !operation.isBlank(), UserOperationLogRecord::getOperation, operation)
                .eq(success != null, UserOperationLogRecord::getSuccess, success)
                .ge(from != null, UserOperationLogRecord::getCreateTime, from)
                .le(to != null, UserOperationLogRecord::getCreateTime, to);
    }
}
