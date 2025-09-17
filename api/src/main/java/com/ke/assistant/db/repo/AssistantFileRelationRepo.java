package com.ke.assistant.db.repo;

import com.ke.assistant.db.generated.tables.pojos.AssistantFileRelationDb;
import com.ke.assistant.db.generated.tables.records.AssistantFileRelationRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ke.assistant.db.generated.Tables.ASSISTANT_FILE_RELATION;

/**
 * Assistant File Relation Repository 自增 ID，不需要生成
 */
@Repository
@RequiredArgsConstructor
public class AssistantFileRelationRepo implements BaseRepo {

    private final DSLContext dsl;

    public AssistantFileRelationDb findById(Integer id) {
        return dsl.selectFrom(ASSISTANT_FILE_RELATION)
                .where(ASSISTANT_FILE_RELATION.ID.eq(id))
                .fetchOneInto(AssistantFileRelationDb.class);
    }

    public List<AssistantFileRelationDb> findByAssistantId(String assistantId) {
        return dsl.selectFrom(ASSISTANT_FILE_RELATION)
                .where(ASSISTANT_FILE_RELATION.ASSISTANT_ID.eq(assistantId))
                .orderBy(ASSISTANT_FILE_RELATION.CREATED_AT.desc())
                .fetchInto(AssistantFileRelationDb.class);
    }

    public List<AssistantFileRelationDb> findByFileId(String fileId) {
        return dsl.selectFrom(ASSISTANT_FILE_RELATION)
                .where(ASSISTANT_FILE_RELATION.FILE_ID.eq(fileId))
                .orderBy(ASSISTANT_FILE_RELATION.CREATED_AT.desc())
                .fetchInto(AssistantFileRelationDb.class);
    }

    public AssistantFileRelationDb findByUniqueKey(String fileId, String assistantId, String toolName) {
        return dsl.selectFrom(ASSISTANT_FILE_RELATION)
                .where(ASSISTANT_FILE_RELATION.FILE_ID.eq(fileId))
                .and(ASSISTANT_FILE_RELATION.ASSISTANT_ID.eq(assistantId))
                .and(ASSISTANT_FILE_RELATION.TOOL_NAME.eq(toolName))
                .fetchOneInto(AssistantFileRelationDb.class);
    }

    public AssistantFileRelationDb insert(AssistantFileRelationDb relation) {
        relation.setObject("assistant.file");
        // 自增 ID，不需要设置
        fillCreateTime(relation);

        AssistantFileRelationRecord record = dsl.newRecord(ASSISTANT_FILE_RELATION, relation);
        record.store();

        return record.into(AssistantFileRelationDb.class);
    }

    public boolean update(AssistantFileRelationDb relation) {
        fillUpdateTime(relation);

        return dsl.update(ASSISTANT_FILE_RELATION)
                .set(dsl.newRecord(ASSISTANT_FILE_RELATION, relation))
                .where(ASSISTANT_FILE_RELATION.ID.eq(relation.getId()))
                .execute() > 0;
    }

    public boolean deleteById(Integer id) {
        return dsl.deleteFrom(ASSISTANT_FILE_RELATION)
                .where(ASSISTANT_FILE_RELATION.ID.eq(id))
                .execute() > 0;
    }

    public boolean deleteByUniqueKey(String fileId, String assistantId, String toolName) {
        return dsl.deleteFrom(ASSISTANT_FILE_RELATION)
                .where(ASSISTANT_FILE_RELATION.FILE_ID.eq(fileId))
                .and(ASSISTANT_FILE_RELATION.ASSISTANT_ID.eq(assistantId))
                .and(ASSISTANT_FILE_RELATION.TOOL_NAME.eq(toolName))
                .execute() > 0;
    }

    public int deleteByAssistantId(String assistantId) {
        return dsl.deleteFrom(ASSISTANT_FILE_RELATION)
                .where(ASSISTANT_FILE_RELATION.ASSISTANT_ID.eq(assistantId))
                .execute();
    }

    public int deleteByAssistantIdWithAllTools(String assistantId) {
        return dsl.deleteFrom(ASSISTANT_FILE_RELATION)
                .where(ASSISTANT_FILE_RELATION.ASSISTANT_ID.eq(assistantId))
                .and(ASSISTANT_FILE_RELATION.TOOL_NAME.eq("_all"))
                .execute();
    }

    public int deleteByAssistantIdWithToolResources(String assistantId) {
        return dsl.deleteFrom(ASSISTANT_FILE_RELATION)
                .where(ASSISTANT_FILE_RELATION.ASSISTANT_ID.eq(assistantId))
                .and(ASSISTANT_FILE_RELATION.TOOL_NAME.ne("_all"))
                .execute();
    }

    public boolean existsById(Integer id) {
        return dsl.fetchExists(
                dsl.selectFrom(ASSISTANT_FILE_RELATION)
                        .where(ASSISTANT_FILE_RELATION.ID.eq(id))
        );
    }

    public boolean existsByUniqueKey(String fileId, String assistantId, String toolName) {
        return dsl.fetchExists(
                dsl.selectFrom(ASSISTANT_FILE_RELATION)
                        .where(ASSISTANT_FILE_RELATION.FILE_ID.eq(fileId))
                        .and(ASSISTANT_FILE_RELATION.ASSISTANT_ID.eq(assistantId))
                        .and(ASSISTANT_FILE_RELATION.TOOL_NAME.eq(toolName))
        );
    }
}
