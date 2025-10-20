package com.ke.assistant.db.repo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.TableField;

import com.ke.assistant.db.context.RepoContext;

/**
 * 基础 Repository 接口
 */
public interface BaseRepo {

    /**
     * 判断是否处于 no-store 模式
     * @return true 如果处于 no-store 模式，false 否则
     */
    default boolean isNoStoreMode() {
        return RepoContext.isActive();
    }

    /**
     * 获取 RepoContext Store
     * @return RepoContext.Store 如果处于 no-store 模式，null 否则
     */
    default RepoContext.Store getContextStore() {
        return RepoContext.store();
    }

    /**
     * 填充创建时间信息
     */
    default void fillCreateTime(Object object) {
        if(object instanceof Timed timed) {
            LocalDateTime now = LocalDateTime.now();
            timed.setCreatedAt(now);
            timed.setUpdatedAt(now);
        }
    }

    /**
     * 填充更新时间信息
     */
    default void fillUpdateTime(Object object) {
        if(object instanceof Timed timed) {
            timed.setUpdatedAt(LocalDateTime.now());
        }
    }

    /**
     * 通用的基于游标的分页查询
     * 
     * @param dsl DSL上下文
     * @param table 查询的表
     * @param baseCondition 基础查询条件
     * @param createdAtField 创建时间字段
     * @param after 游标after参数
     * @param before 游标before参数
     * @param limit 限制条数
     * @param order 排序方式 (asc/desc)
     * @param findByIdFunc 根据ID查找实体的函数
     * @param resultClass 结果类型
     * @return 查询结果列表
     */
    default <R extends Record, T> List<T> findWithCursor(
            DSLContext dsl,
            Table<R> table,
            Condition baseCondition,
            TableField<R, LocalDateTime> createdAtField,
            String after,
            String before,
            int limit,
            String order,
            Function<String, ? extends Timed> findByIdFunc,
            Class<T> resultClass) {
        
        var condition = baseCondition;

        // 处理游标条件
        if (StringUtils.isNotBlank(after)) {
            var afterEntity = findByIdFunc.apply(after);
            if (afterEntity != null) {
                if ("asc".equalsIgnoreCase(order)) {
                    condition = condition.and(createdAtField.gt(afterEntity.getCreatedAt()));
                } else {
                    condition = condition.and(createdAtField.lt(afterEntity.getCreatedAt()));
                }
            }
        }

        if (StringUtils.isNotBlank(before)) {
            var beforeEntity = findByIdFunc.apply(before);
            if (beforeEntity != null) {
                if ("asc".equalsIgnoreCase(order)) {
                    condition = condition.and(createdAtField.lt(beforeEntity.getCreatedAt()));
                } else {
                    condition = condition.and(createdAtField.gt(beforeEntity.getCreatedAt()));
                }
            }
        }

        // 构建最终查询并添加排序和限制
        if ("asc".equalsIgnoreCase(order)) {
            return dsl.selectFrom(table)
                    .where(condition)
                    .orderBy(createdAtField.asc())
                    .limit(limit)
                    .fetchInto(resultClass);
        } else {
            return dsl.selectFrom(table)
                    .where(condition)
                    .orderBy(createdAtField.desc())
                    .limit(limit)
                    .fetchInto(resultClass);
        }
    }
}
