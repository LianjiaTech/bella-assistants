package com.ke.assistant.db.repo;

import com.ke.assistant.db.generated.tables.pojos.RunToolDb;
import com.ke.assistant.db.generated.tables.records.RunToolRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ke.assistant.db.generated.Tables.RUN_TOOL;

/**
 * Run Tool Repository 自增 ID，不需要生成
 */
@Repository
@RequiredArgsConstructor
public class RunToolRepo implements BaseRepo {

    private final DSLContext dsl;

    public RunToolDb findById(Integer id) {
        return dsl.selectFrom(RUN_TOOL)
                .where(RUN_TOOL.ID.eq(id))
                .fetchOneInto(RunToolDb.class);
    }

    public List<RunToolDb> findByRunId(String runId) {
        return dsl.selectFrom(RUN_TOOL)
                .where(RUN_TOOL.RUN_ID.eq(runId))
                .orderBy(RUN_TOOL.CREATED_AT.desc())
                .fetchInto(RunToolDb.class);
    }

    public RunToolDb insert(RunToolDb tool) {
        // 自增 ID，不需要设置
        fillCreateTime(tool);

        RunToolRecord record = dsl.newRecord(RUN_TOOL, tool);
        record.store();

        return record.into(RunToolDb.class);
    }

    public boolean update(RunToolDb tool) {
        fillUpdateTime(tool);

        return dsl.update(RUN_TOOL)
                .set(dsl.newRecord(RUN_TOOL, tool))
                .where(RUN_TOOL.ID.eq(tool.getId()))
                .execute() > 0;
    }

    public boolean existsById(Integer id) {
        return dsl.fetchExists(
                dsl.selectFrom(RUN_TOOL)
                        .where(RUN_TOOL.ID.eq(id))
        );
    }
}
