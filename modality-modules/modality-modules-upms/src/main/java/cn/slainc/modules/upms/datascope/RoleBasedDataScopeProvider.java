package cn.slainc.modules.upms.datascope;

import cn.slainc.core.context.UserContext;
import cn.slainc.mybatis.datascope.DataScope;
import cn.slainc.mybatis.datascope.DataScopeCondition;
import cn.slainc.mybatis.datascope.DataScopeProvider;
import cn.slainc.modules.upms.entity.DataScopeType;
import cn.slainc.modules.upms.entity.RoleDataScopeRecord;
import cn.slainc.modules.upms.entity.RoleRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.modules.upms.service.DeptService;
import cn.slainc.modules.upms.service.RoleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// 角色维度数据权限：多角色取最大范围，部门类范围并集后与本人条件 OR 组合；无角色按仅本人兜底
// 依赖必须走 ObjectProvider 懒解析：拦截器链创建时会实例化本 Bean，若直持 mapper 依赖会与 SqlSessionFactory 成环
public class RoleBasedDataScopeProvider implements DataScopeProvider {

    private static final Logger log = LoggerFactory.getLogger(RoleBasedDataScopeProvider.class);

    private final ObjectProvider<RoleService> roleService;

    private final ObjectProvider<RoleDataScopeMapperBridge> roleDataScopeMapper;

    private final ObjectProvider<DeptService> deptService;

    private final ObjectProvider<UserMapper> userMapper;

    public RoleBasedDataScopeProvider(ObjectProvider<RoleService> roleService,
                                      ObjectProvider<RoleDataScopeMapperBridge> roleDataScopeMapper,
                                      ObjectProvider<DeptService> deptService,
                                      ObjectProvider<UserMapper> userMapper) {
        this.roleService = roleService;
        this.roleDataScopeMapper = roleDataScopeMapper;
        this.deptService = deptService;
        this.userMapper = userMapper;
    }

    @Override
    public DataScopeCondition resolve(DataScope dataScope, String statementId) {
        if (UserContext.isAuthenticated() && UserContext.isSuperAdmin()) {
            return DataScopeCondition.noRestriction();
        }
        Long userId = UserContext.userId();
        if (userId == null) {
            return DataScopeCondition.noRestriction();
        }
        UserRecord user = userMapper.getIfAvailable() == null ? null : userMapper.getObject().selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
            return DataScopeCondition.of(userColumn(dataScope) + " = " + userId);
        }
        RoleService roles = roleService.getIfAvailable();
        List<RoleRecord> grantedRoles = roles == null ? List.of() : roles.resolveUserRoles(userId);
        if (grantedRoles.isEmpty()) {
            return DataScopeCondition.of(userColumn(dataScope) + " = " + userId);
        }
        boolean all = grantedRoles.stream().anyMatch(role -> DataScopeType.ALL.name().equals(role.getDataScope()));
        if (all) {
            return DataScopeCondition.noRestriction();
        }
        Set<Long> deptIds = new LinkedHashSet<>();
        boolean selfOnly = false;
        for (RoleRecord role : grantedRoles) {
            DataScopeType type = DataScopeType.of(role.getDataScope());
            switch (type) {
                case DEPT -> {
                    if (user.getDeptId() != null) {
                        deptIds.add(user.getDeptId());
                    } else {
                        selfOnly = true;
                    }
                }
                case DEPT_AND_CHILD -> {
                    if (user.getDeptId() != null && deptService.getIfAvailable() != null) {
                        deptIds.addAll(deptService.getObject().collectSubtreeIds(user.getDeptId()));
                    } else {
                        selfOnly = true;
                    }
                }
                case CUSTOM -> {
                    List<Long> custom = customDeptIds(role.getId());
                    if (custom.isEmpty()) {
                        selfOnly = true;
                    } else {
                        deptIds.addAll(custom);
                    }
                }
                case SELF -> selfOnly = true;
                default -> log.debug("未知数据权限范围 {} 按仅本人处理", role.getDataScope());
            }
        }
        List<String> expressions = new ArrayList<>();
        if (!deptIds.isEmpty() && dataScope.deptScope()) {
            expressions.add(deptColumn(dataScope) + " IN (" + join(deptIds) + ")");
        }
        if (deptIds.isEmpty() || selfOnly || !dataScope.deptScope()) {
            expressions.add(userColumn(dataScope) + " = " + userId);
        }
        if (expressions.isEmpty()) {
            expressions.add(userColumn(dataScope) + " = " + userId);
        }
        return DataScopeCondition.of(expressions.toArray(String[]::new));
    }

    private List<Long> customDeptIds(Long roleId) {
        RoleDataScopeMapperBridge bridge = roleDataScopeMapper.getIfAvailable();
        if (bridge == null) {
            return List.of();
        }
        return bridge.selectDeptIds(roleId);
    }

    private String userColumn(DataScope dataScope) {
        return column(dataScope.alias(), dataScope.userColumn());
    }

    private String deptColumn(DataScope dataScope) {
        return column(dataScope.alias(), dataScope.deptColumn());
    }

    private String column(String alias, String column) {
        return alias == null || alias.isBlank() ? column : alias + "." + column;
    }

    private String join(Set<Long> ids) {
        StringBuilder builder = new StringBuilder();
        for (Long id : ids) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id);
        }
        return builder.toString();
    }
}
