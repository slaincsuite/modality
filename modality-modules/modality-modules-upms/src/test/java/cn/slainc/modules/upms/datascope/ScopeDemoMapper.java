package cn.slainc.modules.upms.datascope;

import cn.slainc.mybatis.datascope.DataScope;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScopeDemoMapper extends BaseMapper<ScopeDemoRecord> {

    @DataScope(userScope = true, deptScope = true, userColumn = "created_by", deptColumn = "dept_id")
    @Select("SELECT * FROM t_scope_demo")
    java.util.List<ScopeDemoRecord> selectWithScope();
}
