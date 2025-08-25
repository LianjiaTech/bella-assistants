package com.ke.assistant.db.repo;

import com.ke.assistant.db.IdGenerator;
import com.ke.assistant.db.generated.tables.pojos.RunStepDb;
import com.ke.assistant.db.generated.tables.records.RunStepRecord;
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import static com.ke.assistant.db.generated.Tables.RUN_STEP;

/**
 * Run Step Repository 运行步骤数据访问层
 */
@Repository
@RequiredArgsConstructor
public class RunStepRepo implements BaseRepo {

    private final DSLContext dsl;
    private final IdGenerator idGenerator;

    /**
     * 根据 ID 查询 Run Step
     */
    public RunStepDb findById(String id) {
        return dsl.selectFrom(RUN_STEP)
                .where(RUN_STEP.ID.eq(id))
                .fetchOneInto(RunStepDb.class);
    }

    /**
     * 根据 ID 查询 Run Step forUpdate
     */
    public RunStepDb findByIdForUpdate(String id) {
        return dsl.selectFrom(RUN_STEP)
                .where(RUN_STEP.ID.eq(id))
                .forUpdate()
                .fetchOneInto(RunStepDb.class);
    }


    /**
     * 根据 RunID 查询 正在等待工具结果的 Run Step forUpdate
     */
    public RunStepDb findActionRequiredForUpdate(String runId) {
        return dsl.selectFrom(RUN_STEP)
                .where(RUN_STEP.ID.eq(runId))
                .and(RUN_STEP.STATUS.eq("requires_action"))
                .forUpdate()
                .fetchAnyInto(RunStepDb.class);
    }

    /**
     * 根据 Run ID 查询 Run Step 列表
     */
    public List<RunStepDb> findByRunId(String runId) {
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
     * 根据 Run ID 和 Type 查询 Run Step 列表
     */
    public List<RunStepDb> findByRunIdAndType(String runId, String type) {
        return dsl.selectFrom(RUN_STEP)
                .where(RUN_STEP.RUN_ID.eq(runId))
                .and(RUN_STEP.TYPE.eq(type))
                .orderBy(RUN_STEP.CREATED_AT.asc())
                .fetchInto(RunStepDb.class);
    }


    /**
     * 插入 Run Step
     */
    public RunStepDb insert(RunStepDb runStep) {
        if(StringUtils.isBlank(runStep.getId())) {
            runStep.setId(idGenerator.generateRunStepId());
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
    public boolean updateStepDetails(String id, String stepDetails) {
        return dsl.update(RUN_STEP)
                .set(RUN_STEP.STEP_DETAILS, stepDetails)
                .set(RUN_STEP.UPDATED_AT, java.time.LocalDateTime.now())
                .where(RUN_STEP.ID.eq(id))
                .execute() > 0;
    }

    /**
     * 删除 Run Step
     */
    public boolean deleteById(String id) {
        return dsl.deleteFrom(RUN_STEP)
                .where(RUN_STEP.ID.eq(id))
                .execute() > 0;
    }

    /**
     * 检查 Run Step 是否存在
     */
    public boolean existsById(String id) {
        return dsl.fetchExists(
                dsl.selectFrom(RUN_STEP)
                        .where(RUN_STEP.ID.eq(id))
        );
    }
}
