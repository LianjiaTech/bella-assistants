package com.ke.assistant.db.repo;

import java.time.LocalDateTime;

/**
 * 时间审计接口 用于标识包含创建时间和更新时间的实体
 */
public interface Timed {

    /**
     * 获取创建时间
     */
    LocalDateTime getCreatedAt();

    /**
     * 设置创建时间
     */
    void setCreatedAt(LocalDateTime createdAt);

    /**
     * 获取更新时间
     */
    LocalDateTime getUpdatedAt();

    /**
     * 设置更新时间
     */
    void setUpdatedAt(LocalDateTime updatedAt);
}
