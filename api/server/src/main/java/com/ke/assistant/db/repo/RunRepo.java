package com.ke.assistant.db.repo;

import com.ke.assistant.db.IdGenerator;
import com.ke.assistant.db.generated.tables.pojos.RunDb;
import com.ke.assistant.db.generated.tables.records.RunRecord;
import lombok.RequiredArgsConstructor;
import lombok.var;
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
        return dsl.selectFrom(RUN)
                .where(RUN.ID.eq(id))
                .fetchOneInto(RunDb.class);
    }

    /**
     * 根据 ID 查询 Run
     */
    public RunDb findByIdForUpdate(String threadId, String id) {
        return dsl.selectFrom(RUN)
                .where(RUN.ID.eq(id))
                .forUpdate()
                .fetchOneInto(RunDb.class);
    }

    /**
     * 根据 Thread ID 查询 Run 列表
     */
    public List<RunDb> findByThreadId(String threadId) {
        return dsl.selectFrom(RUN)
                .where(RUN.THREAD_ID.eq(threadId))
                .orderBy(RUN.CREATED_AT.desc())
                .fetchInto(RunDb.class);
    }

    /**
     * 分页查询 Thread 下的 Run
     */
    public Page<RunDb> findByThreadIdWithPage(String threadId, int page, int pageSize) {
        var query = dsl.selectFrom(RUN)
                .where(RUN.THREAD_ID.eq(threadId))
                .orderBy(RUN.CREATED_AT.desc());

        return queryPage(dsl, query, page, pageSize, RunDb.class);
    }

    /**
     * 插入 Run
     */
    public RunDb insert(RunDb run) {
        if(StringUtils.isBlank(run.getId())) {
            run.setId(idGenerator.generateRunId());
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
        fillUpdateTime(run);

        return dsl.update(RUN)
                .set(dsl.newRecord(RUN, run))
                .where(RUN.ID.eq(run.getId()))
                .execute() > 0;
    }

    public boolean updateRequireAction(String threadId, String id, String requireAction) {
        return dsl.update(RUN)
                .set(RUN.REQUIRED_ACTION, requireAction)
                .set(RUN.UPDATED_AT, LocalDateTime.now())
                .where(RUN.ID.eq(id))
                .execute() > 0;
    }
}
