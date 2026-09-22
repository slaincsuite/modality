package cn.slainc.modules.upms.excel;

import cn.idev.excel.annotation.ExcelProperty;

// 用户导出行：手机号与邮箱在导出侧脱敏，原始值不出现在文件里
public class UserExportRow {

    @ExcelProperty("用户ID")
    private Long userId;

    @ExcelProperty("用户名")
    private String username;

    @ExcelProperty("昵称")
    private String nickname;

    @ExcelProperty("账号体系")
    private String loginType;

    @ExcelProperty("手机号")
    private String phone;

    @ExcelProperty("邮箱")
    private String email;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("创建时间")
    private String createTime;

    public UserExportRow() {
    }

    public UserExportRow(Long userId, String username, String nickname, String loginType, String phone, String email,
                         String status, String createTime) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.loginType = loginType;
        this.phone = phone;
        this.email = email;
        this.status = status;
        this.createTime = createTime;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getLoginType() {
        return loginType;
    }

    public void setLoginType(String loginType) {
        this.loginType = loginType;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }
}
