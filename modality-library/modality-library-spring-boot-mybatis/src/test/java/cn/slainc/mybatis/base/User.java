package cn.slainc.core.base;

import cn.slainc.mybatis.annotation.EncryptField;
import cn.slainc.mybatis.annotation.Sensitive;
import cn.slainc.mybatis.annotation.SensitiveType;
import cn.slainc.mybatis.handler.JsonTypeHandler;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Map;

@TableName(value = "t_user", autoResultMap = true)
public class User extends cn.slainc.core.base.MybatisBaseEntity {

    private String name;

    @Sensitive(type = SensitiveType.PHONE)
    private String phone;

    @EncryptField
    private String secret;

    @TableField(value = "remark", typeHandler = JsonTypeHandler.class)
    private Map<String, Object> remark;

    @TableField("dept_id")
    private Long deptId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Map<String, Object> getRemark() {
        return remark;
    }

    public void setRemark(Map<String, Object> remark) {
        this.remark = remark;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }
}
