package com.ke.assistant.db.repo;

import com.ke.assistant.db.IdGenerator;
import com.ke.assistant.db.generated.tables.pojos.ThreadDb;
import com.ke.assistant.db.generated.tables.records.ThreadRecord;
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ke.assistant.db.generated.Tables.THREAD;

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
        return dsl.selectFrom(THREAD)
                .where(THREAD.ID.eq(id))
                .fetchOneInto(ThreadDb.class);
    }

    /**
     * 根据 owner 查询 Thread 列表
     */
    public List<ThreadDb> findByOwner(String owner) {
        return dsl.selectFrom(THREAD)
                .where(THREAD.OWNER.eq(owner))
                .orderBy(THREAD.CREATED_AT.desc())
                .fetchInto(ThreadDb.class);
    }

    /**
     * 分页查询 Thread
     */
    public Page<ThreadDb> findByOwnerWithPage(String owner, int page, int pageSize) {
        var query = dsl.selectFrom(THREAD)
                .where(THREAD.OWNER.eq(owner))
                .orderBy(THREAD.CREATED_AT.desc());

        return queryPage(dsl, query, page, pageSize, ThreadDb.class);
    }

    /**
     * 插入 Thread
     */
    public ThreadDb insert(ThreadDb thread) {
        if(StringUtils.isBlank(thread.getId())) {
            thread.setId(idGenerator.generateThreadId());
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
        return dsl.deleteFrom(THREAD)
                .where(THREAD.ID.eq(id))
                .execute() > 0;
    }

    /**
     * 检查 Thread 是否存在
     */
    public boolean existsById(String id) {
        return dsl.fetchExists(
                dsl.selectFrom(THREAD)
                        .where(THREAD.ID.eq(id))
        );
    }

    /**
     * 检查 Thread 所有者权限
     */
    public boolean checkOwnership(String id, String owner) {
        return dsl.fetchExists(
                dsl.selectFrom(THREAD)
                        .where(THREAD.ID.eq(id))
                        .and(THREAD.OWNER.eq(owner))
        );
    }
}
