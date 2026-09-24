package cn.slainc.modules.upms.mapper;

import cn.slainc.modules.upms.entity.Batch8ScopeRow;
import cn.slainc.mybatis.datascope.DataScope;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface Batch8ScopeMapper extends BaseMapper<Batch8ScopeRow> {

    @DataScope(userScope = true, deptScope = true, userColumn = "created_by", deptColumn = "dept_id")
    @Select("SELECT * FROM t_batch8_scope")
    List<Batch8ScopeRow> selectScoped();
}
