package cn.slainc.core.entity;

import cn.slainc.core.page.PageParam;
import cn.slainc.core.page.Sort;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class BaseQuery implements Serializable {

    private long pageNum = 1;

    private long pageSize = PageParam.DEFAULT_SIZE;

    private String orderBy;

    private String orderDirection = "ASC";

    public long getPageNum() {
        return pageNum;
    }

    public void setPageNum(long pageNum) {
        this.pageNum = pageNum;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }

    public String getOrderDirection() {
        return orderDirection;
    }

    public void setOrderDirection(String orderDirection) {
        this.orderDirection = orderDirection;
    }

    public PageParam toPageParam() {
        List<Sort> sorts = new ArrayList<>();
        if (orderBy != null && !orderBy.isBlank()) {
            boolean descending = "DESC".equalsIgnoreCase(orderDirection) || "DESCENDING".equalsIgnoreCase(orderDirection);
            sorts.add(Sort.of(orderBy, descending ? Sort.Direction.DESC : Sort.Direction.ASC));
        }
        return new PageParam(pageNum, pageSize, sorts).normalized();
    }
}
