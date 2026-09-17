package cn.slainc.apikey;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName(ApiKeyRecord.TABLE_NAME)
public class ApiKeyRecord {

    public static final String TABLE_NAME = "t_api_key";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String apiKey;

    private String title;

    private String intro;

    private String loginId;

    private String scopes;

    private String extraData;

    private String status = ApiKeyStatus.ENABLED;

    private Long expiresTime;

    private Long createTime;

    private Long updateTime;

    public boolean expired(long now) {
        return expiresTime != null && expiresTime > 0 && now > expiresTime;
    }

    public boolean active(long now) {
        return ApiKeyStatus.ENABLED.equals(status) && !expired(now);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getIntro() {
        return intro;
    }

    public void setIntro(String intro) {
        this.intro = intro;
    }

    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public String getExtraData() {
        return extraData;
    }

    public void setExtraData(String extraData) {
        this.extraData = extraData;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getExpiresTime() {
        return expiresTime;
    }

    public void setExpiresTime(Long expiresTime) {
        this.expiresTime = expiresTime;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }
}
