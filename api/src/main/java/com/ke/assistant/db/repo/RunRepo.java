package com.ke.assistant.db.repo;

import com.ke.assistant.db.IdGenerator;
import com.ke.assistant.db.context.RepoContext;
import com.ke.assistant.db.generated.tables.pojos.RunDb;
import com.ke.assistant.db.generated.tables.records.RunRecord;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import static com.ke.assistant.db.generated.Tables.RUN;

/**
 * Run Repository 运行记录数据访问层
 * todo: threadId 用于分表
 */
@Repository
@RequiredArgsConstructor
public class RunRepo implements BaseRepo {

    private final DSLContext dsl;
    private final IdGenerator idGenerator;

    /**
     * 根据 ID 查询 Run
     */
    public RunDb findById(String threadId, String id) {
        
        if (isNoStoreMode()) {
            return getContextStore().findRunById(id);
        }
        return dsl.selectFrom(RUN)
                .where(RUN.ID.eq(id))
                .fetchOneInto(RunDb.class);
    }

    /**
     * 根据 ID 查询 Run
     */
    public RunDb findByIdForUpdate(String threadId, String id) {
        
        if (isNoStoreMode()) {
            return getContextStore().findRunById(id);
        }
        return dsl.selectFrom(RUN)
                .where(RUN.ID.eq(id))
                .forUpdate()
                .fetchOneInto(RunDb.class);
    }

    /**
     * 根据 Thread ID 查询 Run 列表
     */
    public List<RunDb> findByThreadId(String threadId) {
        
        if (isNoStoreMode()) {
            return getContextStore().findRunsByThreadId(threadId);
        }
        return dsl.selectFrom(RUN)
                .where(RUN.THREAD_ID.eq(threadId))
                .orderBy(RUN.CREATED_AT.desc())
                .fetchInto(RunDb.class);
    }

    /**
     * 根据 Thread ID 查询 任意Run
     */
    public RunDb findAnyByThreadId(String threadId) {
        
        if (isNoStoreMode()) {
            return getContextStore().findAnyRunByThreadId(threadId);
        }
        return dsl.selectFrom(RUN)
                .where(RUN.THREAD_ID.eq(threadId))
                .orderBy(RUN.CREATED_AT.desc())
                .limit(1)
                .fetchOneInto(RunDb.class);
    }


    /**
     * 基于游标的分页查询 Thread 下的 Run
     */
    public List<RunDb> findByThreadIdWithCursor(String threadId, String after, String before, int limit, String order) {
        
        if (isNoStoreMode()) {
            // Simplify to recent runs in non-store mode
            return getContextStore().findRunsByThreadId(threadId);
        }
        return findWithCursor(
                dsl,
                RUN,
                RUN.THREAD_ID.eq(threadId),
                RUN.CREATED_AT,
                after,
                before,
                limit,
                order,
                id -> findById(threadId, id),
                RunDb.class
        );
    }

    /**
     * 插入 Run
     */
    public RunDb insert(RunDb run) {
        run.setObject("thread.run");
        if(StringUtils.isBlank(run.getId())) {
            run.setId(idGenerator.generateRunId());
        }

        if (isNoStoreMode()) {
            return getContextStore().insertRun(run);
        }
        fillCreateTime(run);

        RunRecord record = dsl.newRecord(RUN, run);
        record.store();

        return record.into(RunDb.class);
    }

    /**
     * 更新 Run
     */
    public boolean update(RunDb run) {
        
        if (isNoStoreMode()) {
            return getContextStore().updateRun(run);
        }
        fillUpdateTime(run);

        return dsl.update(RUN)
                .set(dsl.newRecord(RUN, run))
                .where(RUN.ID.eq(run.getId()))
                .execute() > 0;
    }

    public boolean updateRequireAction(String threadId, String id, String requireAction) {
        
        if (isNoStoreMode()) {
            RunDb db = getContextStore().findRunById(id);
            if (db == null) return false;
            db.setRequiredAction(requireAction);
            db.setUpdatedAt(LocalDateTime.now());
            return getContextStore().updateRun(db);
        }
        return dsl.update(RUN)
                .set(RUN.REQUIRED_ACTION, requireAction)
                .set(RUN.UPDATED_AT, LocalDateTime.now())
                .where(RUN.ID.eq(id))
                .execute() > 0;
    }
}
