package cn.slainc.modules.infra.entity;

import cn.slainc.modules.infra.InfraKeys;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;


@TableName(SearchHistoryRecord.TABLE_NAME)
public class SearchHistoryRecord {

    public static final String TABLE_NAME = InfraKeys.SEARCH_HISTORY_TABLE;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String keyword;

    private Long searchedAt;

    private Long tenantId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getSearchedAt() {
        return searchedAt;
    }

    public void setSearchedAt(Long searchedAt) {
        this.searchedAt = searchedAt;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
}
