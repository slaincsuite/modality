package cn.slainc.modules.infra.entity;

import cn.slainc.modules.infra.InfraKeys;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;


@TableName(SearchIndexTermRecord.TABLE_NAME)
public class SearchIndexTermRecord {

    public static final String TABLE_NAME = InfraKeys.SEARCH_INDEX_TERM_TABLE;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private String term;

    private String field;

    private Integer weight;

    private Long tenantId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
}
