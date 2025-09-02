package com.ke.assistant.db.repo;

import com.ke.assistant.db.generated.tables.pojos.AssistantToolDb;
import com.ke.assistant.db.generated.tables.records.AssistantToolRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ke.assistant.db.generated.Tables.ASSISTANT_TOOL;

/**
 * Assistant Tool Repository 自增 ID，不需要生成
 */
@Repository
@RequiredArgsConstructor
public class AssistantToolRepo implements BaseRepo {

    private final DSLContext dsl;

    public AssistantToolDb findById(Integer id) {
        return dsl.selectFrom(ASSISTANT_TOOL)
                .where(ASSISTANT_TOOL.ID.eq(id))
                .fetchOneInto(AssistantToolDb.class);
    }

    public List<AssistantToolDb> findByAssistantId(String assistantId) {
        return dsl.selectFrom(ASSISTANT_TOOL)
                .where(ASSISTANT_TOOL.ASSISTANT_ID.eq(assistantId))
                .orderBy(ASSISTANT_TOOL.CREATED_AT.desc())
                .fetchInto(AssistantToolDb.class);
    }

    public AssistantToolDb insert(AssistantToolDb tool) {
        // 自增 ID，不需要设置
        fillCreateTime(tool);

        AssistantToolRecord record = dsl.newRecord(ASSISTANT_TOOL, tool);
        record.store();

        return record.into(AssistantToolDb.class);
    }

    public boolean update(AssistantToolDb tool) {
        fillUpdateTime(tool);

        return dsl.update(ASSISTANT_TOOL)
                .set(dsl.newRecord(ASSISTANT_TOOL, tool))
                .where(ASSISTANT_TOOL.ID.eq(tool.getId()))
                .execute() > 0;
    }

    public boolean deleteById(Integer id) {
        return dsl.deleteFrom(ASSISTANT_TOOL)
                .where(ASSISTANT_TOOL.ID.eq(id))
                .execute() > 0;
    }

    public int deleteByAssistantId(String assistantId) {
        return dsl.deleteFrom(ASSISTANT_TOOL)
                .where(ASSISTANT_TOOL.ASSISTANT_ID.eq(assistantId))
                .execute();
    }

    public boolean existsById(Integer id) {
        return dsl.fetchExists(
                dsl.selectFrom(ASSISTANT_TOOL)
                        .where(ASSISTANT_TOOL.ID.eq(id))
        );
    }
}
