package cn.slainc.mybatis.page;

import cn.slainc.core.page.PageParam;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.io.Serializable;

public class PageQuery implements Serializable {

    public static final long DEFAULT_PAGE_NUM = 1L;

    private Long pageNum;

    private Long pageSize;

    private Long cursor;

    private String orderBy;

    private Boolean asc;

    public PageQuery() {
    }

    public PageQuery(Long pageNum, Long pageSize) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    public long normalizedPageNum() {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    public long normalizedPageSize() {
        if (pageSize == null || pageSize < 1) {
            return PageParam.DEFAULT_SIZE;
        }
        return Math.min(pageSize, PageParam.MAX_SIZE);
    }

    public <T> Page<T> toPage() {
        Page<T> page = new Page<>(normalizedPageNum(), normalizedPageSize());
        if (orderBy != null && !orderBy.isBlank()) {
            page.addOrder(Boolean.FALSE.equals(asc)
                    ? com.baomidou.mybatisplus.core.metadata.OrderItem.desc(orderBy)
                    : com.baomidou.mybatisplus.core.metadata.OrderItem.asc(orderBy));
        }
        return page;
    }

    public <T> Page<T> toPage(boolean searchCount) {
        Page<T> page = toPage();
        page.setSearchCount(searchCount);
        return page;
    }

    public Long getPageNum() {
        return pageNum;
    }

    public void setPageNum(Long pageNum) {
        this.pageNum = pageNum;
    }

    public Long getPageSize() {
        return pageSize;
    }

    public void setPageSize(Long pageSize) {
        this.pageSize = pageSize;
    }

    public Long getCursor() {
        return cursor;
    }

    public void setCursor(Long cursor) {
        this.cursor = cursor;
    }

    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }

    public Boolean getAsc() {
        return asc;
    }

    public void setAsc(Boolean asc) {
        this.asc = asc;
    }
}
