package com.ke.assistant.db.repo;

import com.ke.assistant.db.IdGenerator;
import com.ke.assistant.db.generated.tables.pojos.RunStepDb;
import com.ke.assistant.db.generated.tables.records.RunStepRecord;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ke.assistant.db.generated.Tables.RUN_STEP;

/**
 * Run Step Repository 运行步骤数据访问层
 * todo: threadId 用于分表
 */
@Repository
@RequiredArgsConstructor
public class RunStepRepo implements BaseRepo {

    private final DSLContext dsl;
    private final IdGenerator idGenerator;

    /**
     * 根据 ID 查询 Run Step
     */
    public RunStepDb findById(String threadId, String id) {
        return dsl.selectFrom(RUN_STEP)
                .where(RUN_STEP.ID.eq(id))
                .fetchOneInto(RunStepDb.class);
    }

    /**
     * 根据 ID list 查询 Run Step
     */
    public List<RunStepDb> findByIds(String threadId, List<String> ids) {
        return dsl.selectFrom(RUN_STEP)
                .where(RUN_STEP.ID.in(ids))
                .fetchInto(RunStepDb.class);
    }

    /**
     * 根据 ID 查询 Run Step forUpdate
     */
    public RunStepDb findByIdForUpdate(String threadId, String id) {
        return dsl.selectFrom(RUN_STEP)
                .where(RUN_STEP.ID.eq(id))
                .forUpdate()
                .fetchOneInto(RunStepDb.class);
    }


    /**
     * 根据 RunID 查询 正在等待工具结果的 Run Step forUpdate
     */
    public RunStepDb findActionRequiredForUpdate(String threadId, String runId) {
        return dsl.selectFrom(RUN_STEP)
                .where(RUN_STEP.RUN_ID.eq(runId))
                .and(RUN_STEP.STATUS.eq("requires_action"))
                .forUpdate()
                .fetchAnyInto(RunStepDb.class);
    }

    /**
     * 根据 Run ID 查询 Run Step 列表
     */
    public List<RunStepDb> findByRunId(String threadId, String runId) {
        return dsl.selectFrom(RUN_STEP)
                .where(RUN_STEP.RUN_ID.eq(runId))
                .orderBy(RUN_STEP.CREATED_AT.asc())
                .fetchInto(RunStepDb.class);
    }

    /**
     * 根据 Thread ID 查询 Run Step 列表
     */
    public List<RunStepDb> findByThreadId(String threadId) {
        return dsl.selectFrom(RUN_STEP)
                .where(RUN_STEP.THREAD_ID.eq(threadId))
                .orderBy(RUN_STEP.CREATED_AT.asc())
                .fetchInto(RunStepDb.class);
    }

    /**
     * 根据 Thread ID 和 Run IDs 查询 Run Step 列表
     */
    public List<RunStepDb> findByRunIds(String threadId, List<String> runIds) {
        return dsl.selectFrom(RUN_STEP)
                .where(RUN_STEP.THREAD_ID.eq(threadId))
                .and(RUN_STEP.RUN_ID.in(runIds))
                .orderBy(RUN_STEP.CREATED_AT.asc())
                .fetchInto(RunStepDb.class);
    }

    /**
     * 根据 Run ID 查询 Run Step 列表（支持游标分页）
     */
    public List<RunStepDb> findByRunIdWithCursor(String threadId, String runId, String after, String before, int limit, String order) {
        return findWithCursor(
                dsl,
                RUN_STEP,
                RUN_STEP.THREAD_ID.eq(threadId).and(RUN_STEP.RUN_ID.eq(runId)),
                RUN_STEP.CREATED_AT,
                after,
                before,
                limit,
                order,
                id -> findById(threadId, id),
                RunStepDb.class
        );
    }


    /**
     * 插入 Run Step
     */
    public RunStepDb insert(RunStepDb runStep) {
        runStep.setObject("thread.run.step");
        if(StringUtils.isBlank(runStep.getId())) {
            runStep.setId(idGenerator.generateRunStepId());
        }
        if(runStep.getMetadata() == null) {
            runStep.setMetadata("{}");
        }

        fillCreateTime(runStep);

        RunStepRecord record = dsl.newRecord(RUN_STEP, runStep);
        record.store();

        return record.into(RunStepDb.class);
    }

    /**
     * 更新 Run Step
     */
    public boolean update(RunStepDb runStep) {
        fillUpdateTime(runStep);

        return dsl.update(RUN_STEP)
                .set(dsl.newRecord(RUN_STEP, runStep))
                .where(RUN_STEP.ID.eq(runStep.getId()))
                .execute() > 0;
    }

    /**
     * 更新 Step Details
     */
    public boolean updateStepDetails(String threadId, String id, String stepDetails) {
        return dsl.update(RUN_STEP)
                .set(RUN_STEP.STEP_DETAILS, stepDetails)
                .set(RUN_STEP.UPDATED_AT, java.time.LocalDateTime.now())
                .where(RUN_STEP.ID.eq(id))
                .execute() > 0;
    }
}
