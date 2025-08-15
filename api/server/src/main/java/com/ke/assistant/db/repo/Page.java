package com.ke.assistant.db.repo;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 分页结果封装
 */
@Data
public class Page<T> {
    private int page;
    private int pageSize;
    private long total;
    private List<T> list;

    public static <T> Page<T> from(int page, int pageSize) {
        Page<T> result = new Page<>();
        result.page = page;
        result.pageSize = pageSize;
        result.total = 0;
        result.list = Collections.emptyList();
        return result;
    }

    public Page<T> total(long total) {
        this.total = total;
        return this;
    }

    public Page<T> list(List<T> list) {
        this.list = list;
        return this;
    }

    /**
     * 计算总页数
     */
    public long getTotalPages() {
        return (total + pageSize - 1) / pageSize;
    }

    /**
     * 是否有下一页
     */
    public boolean hasNext() {
        return page < getTotalPages();
    }

    /**
     * 是否有上一页
     */
    public boolean hasPrevious() {
        return page > 1;
    }
}
