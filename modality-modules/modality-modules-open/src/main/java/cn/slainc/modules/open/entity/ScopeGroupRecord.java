package cn.slainc.modules.open.entity;

public class ScopeGroupRecord {

    private String name;

    private String description;

    private int sort;

    private long createdAt;

    private long updatedAt;

    public ScopeGroupRecord() {
    }

    public ScopeGroupRecord(String name, String description, int sort, long createdAt, long updatedAt) {
        this.name = name;
        this.description = description;
        this.sort = sort;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getSort() {
        return sort;
    }

    public void setSort(int sort) {
        this.sort = sort;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
