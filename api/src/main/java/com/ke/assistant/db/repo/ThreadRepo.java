package com.ke.assistant.db.repo;

import static com.ke.assistant.db.generated.Tables.THREAD;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import com.ke.assistant.db.IdGenerator;
import com.ke.assistant.db.generated.tables.pojos.ThreadDb;
import com.ke.assistant.db.generated.tables.records.ThreadRecord;

import lombok.RequiredArgsConstructor;

/**
 * Thread Repository
 */
@Repository
@RequiredArgsConstructor
public class ThreadRepo implements BaseRepo {

    private final DSLContext dsl;
    private final IdGenerator idGenerator;

    /**
     * 根据 ID 查询 Thread
     */
    public ThreadDb findById(String id) {
        
        if (isNoStoreMode()) {
            return getContextStore().findThreadById(id);
        }
        return dsl.selectFrom(THREAD)
                .where(THREAD.ID.eq(id))
                .fetchOneInto(ThreadDb.class);
    }

    /**
     * 根据 owner 查询 Thread 列表
     */
    public List<ThreadDb> findByOwner(String owner) {
        
        if (isNoStoreMode()) {
            return getContextStore().findThreadsByOwner(owner);
        }
        return dsl.selectFrom(THREAD)
                .where(THREAD.OWNER.eq(owner))
                .orderBy(THREAD.CREATED_AT.desc())
                .fetchInto(ThreadDb.class);
    }


    /**
     * 基于游标的分页查询 Thread
     */
    public List<ThreadDb> findByOwnerWithCursor(String owner, String after, String before, int limit, String order) {
        
        if (isNoStoreMode()) {
            // Simplified non-store mode: return recent owner threads
            return getContextStore().findThreadsByOwner(owner);
        }
        return findWithCursor(
                dsl,
                THREAD,
                THREAD.OWNER.eq(owner),
                THREAD.CREATED_AT,
                after,
                before,
                limit,
                order,
                this::findById,
                ThreadDb.class
        );
    }

    /**
     * 插入 Thread
     */
    public ThreadDb insert(ThreadDb thread) {
        if(StringUtils.isBlank(thread.getId())) {
            thread.setId(idGenerator.generateThreadId());
        }

        
        if (isNoStoreMode()) {
            return getContextStore().insertThread(thread);
        }
        fillCreateTime(thread);

        ThreadRecord record = dsl.newRecord(THREAD, thread);
        record.store();

        return record.into(ThreadDb.class);
    }

    /**
     * 更新 Thread
     */
    public boolean update(ThreadDb thread) {
        
        if (isNoStoreMode()) {
            return getContextStore().updateThread(thread);
        }
        fillUpdateTime(thread);

        return dsl.update(THREAD)
                .set(dsl.newRecord(THREAD, thread))
                .where(THREAD.ID.eq(thread.getId()))
                .execute() > 0;
    }

    /**
     * 删除 Thread
     */
    public boolean deleteById(String id) {
        
        if (isNoStoreMode()) {
            return getContextStore().deleteThreadById(id);
        }
        return dsl.deleteFrom(THREAD)
                .where(THREAD.ID.eq(id))
                .execute() > 0;
    }

    /**
     * 检查 Thread 是否存在
     */
    public boolean existsById(String id) {
        
        if (isNoStoreMode()) {
            return getContextStore().threadExistsById(id);
        }
        return dsl.fetchExists(
                dsl.selectFrom(THREAD)
                        .where(THREAD.ID.eq(id))
        );
    }

    /**
     * 检查 Thread 所有者权限
     */
    public boolean checkOwnership(String id, String owner) {
        
        if (isNoStoreMode()) {
            return getContextStore().threadCheckOwnership(id, owner);
        }
        return dsl.fetchExists(
                dsl.selectFrom(THREAD)
                        .where(THREAD.ID.eq(id))
                        .and(THREAD.OWNER.eq(owner))
        );
    }
}
