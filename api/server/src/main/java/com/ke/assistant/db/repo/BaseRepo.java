package com.ke.assistant.db.repo;

import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.SelectLimitStep;
import org.jooq.UpdatableRecord;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;

/**
 * 基础 Repository 接口
 */
public interface BaseRepo {

    /**
     * 填充创建时间信息
     */
    default void fillCreateTime(Object object) {
        if(object instanceof Timed) {
            Timed timed = (Timed) object;
            LocalDateTime now = LocalDateTime.now();
            timed.setCreatedAt(now);
            timed.setUpdatedAt(now);
        }
    }

    /**
     * 填充更新时间信息
     */
    default void fillUpdateTime(Object object) {
        if(object instanceof Timed) {
            Timed timed = (Timed) object;
            timed.setUpdatedAt(LocalDateTime.now());
        }
    }

    /**
     * 批量执行查询
     */
    default int batchExecuteQuery(DSLContext db, Collection<Query> queries) {
        int[] rows = db.batch(queries).execute();
        int sum = Arrays.stream(rows).sum();
        if(sum < queries.size()) {
            throw new IllegalStateException("批处理失败");
        }
        return sum;
    }

    /**
     * 批量插入
     */
    default int batchInsert(DSLContext db, Collection<? extends UpdatableRecord<?>> records) {
        int[] rows = db.batchInsert(records).execute();
        int sum = Arrays.stream(rows).sum();
        if(sum < records.size()) {
            throw new IllegalStateException("批处理失败");
        }
        return sum;
    }

    /**
     * 分页查询
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    default <T> Page<T> queryPage(DSLContext db, SelectLimitStep scs, int page, int pageSize, Class<?> type) {
        if(scs == null) {
            return Page.from(page, pageSize);
        }
        return Page.from(page, pageSize)
                .total(db.fetchCount(scs))
                .list(scs.limit((page - 1) * pageSize, pageSize)
                        .fetch()
                        .into(type));
    }
}
