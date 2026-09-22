package cn.slainc.modules.upms.datascope;

import cn.slainc.modules.upms.entity.RoleDataScopeRecord;
import cn.slainc.modules.upms.mapper.RoleDataScopeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.List;

// 数据权限 provider 的自定义部门查询桥：Bean 化后可经 ObjectProvider 懒解析，避免拦截器装配期的依赖环
public class RoleDataScopeMapperBridge {

    private final RoleDataScopeMapper mapper;

    public RoleDataScopeMapperBridge(RoleDataScopeMapper mapper) {
        this.mapper = mapper;
    }

    public List<Long> selectDeptIds(Long roleId) {
        return mapper.selectList(new LambdaQueryWrapper<RoleDataScopeRecord>()
                        .eq(RoleDataScopeRecord::getRoleId, roleId))
                .stream().map(RoleDataScopeRecord::getDeptId).toList();
    }
}
