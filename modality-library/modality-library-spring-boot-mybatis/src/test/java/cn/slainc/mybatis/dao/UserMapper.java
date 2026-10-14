package cn.slainc.mybatis.dao;

import cn.slainc.mybatis.datascope.DataScope;
import cn.slainc.mybatis.base.User;
import cn.slainc.mybatis.tenant.IgnoreTenant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserMapper extends BaseMapper<User> {

    @IgnoreTenant
    @Select("SELECT * FROM t_user ORDER BY id")
    List<User> selectAllIgnoringTenant();

    @DataScope(userColumn = "created_by")
    @Select("SELECT * FROM t_user ORDER BY id")
    List<User> selectWithDataScope();

    @Select("SELECT secret FROM t_user WHERE id = #{id}")
    String selectRawSecret(Long id);

    @Select("SELECT * FROM t_user WHERE secret = #{secret}")
    List<User> selectBySecret(User probe);
}
