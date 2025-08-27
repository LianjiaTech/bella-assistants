package com.ke.assistant.db.repo;

import com.ke.assistant.db.IdGenerator;
import com.ke.assistant.db.generated.tables.pojos.AssistantDb;
import com.ke.assistant.db.generated.tables.records.AssistantRecord;
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ke.assistant.db.generated.Tables.ASSISTANT;

/**
 * Assistant Repository 基于 JOOQ 的数据访问层
 */
@Repository
@RequiredArgsConstructor
public class AssistantRepo implements BaseRepo {

    private final DSLContext dsl;
    private final IdGenerator idGenerator;

    /**
     * 根据 ID 查询 Assistant
     */
    public AssistantDb findById(String id) {
        return dsl.selectFrom(ASSISTANT)
                .where(ASSISTANT.ID.eq(id))
                .fetchOneInto(AssistantDb.class);
    }

    /**
     * 根据 owner 查询 Assistant 列表
     */
    public List<AssistantDb> findByOwner(String owner) {
        return dsl.selectFrom(ASSISTANT)
                .where(ASSISTANT.OWNER.eq(owner))
                .orderBy(ASSISTANT.CREATED_AT.desc())
                .fetchInto(AssistantDb.class);
    }


    /**
     * 基于游标的分页查询 Assistant
     */
    public List<AssistantDb> findByOwnerWithCursor(String owner, String after, String before, int limit, String order) {
        return findWithCursor(
                dsl,
                ASSISTANT,
                ASSISTANT.OWNER.eq(owner),
                ASSISTANT.CREATED_AT,
                after,
                before,
                limit,
                order,
                this::findById,
                AssistantDb.class
        );
    }

    /**
     * 插入 Assistant
     */
    public AssistantDb insert(AssistantDb assistant) {
        // 如果没有 ID，则生成一个
        if(StringUtils.isBlank(assistant.getId())) {
            assistant.setId(idGenerator.generateAssistantId());
        }

        fillCreateTime(assistant);

        AssistantRecord record = dsl.newRecord(ASSISTANT, assistant);
        record.store();

        return record.into(AssistantDb.class);
    }

    /**
     * 更新 Assistant
     */
    public boolean update(AssistantDb assistant) {
        fillUpdateTime(assistant);

        return dsl.update(ASSISTANT)
                .set(dsl.newRecord(ASSISTANT, assistant))
                .where(ASSISTANT.ID.eq(assistant.getId()))
                .execute() > 0;
    }

    /**
     * 删除 Assistant
     */
    public boolean deleteById(String id) {
        return dsl.deleteFrom(ASSISTANT)
                .where(ASSISTANT.ID.eq(id))
                .execute() > 0;
    }

    /**
     * 检查 Assistant 是否存在
     */
    public boolean existsById(String id) {
        return dsl.fetchExists(
                dsl.selectFrom(ASSISTANT)
                        .where(ASSISTANT.ID.eq(id))
        );
    }

    /**
     * 检查 Assistant 所有者权限
     */
    public boolean checkOwnership(String id, String owner) {
        return dsl.fetchExists(
                dsl.selectFrom(ASSISTANT)
                        .where(ASSISTANT.ID.eq(id))
                        .and(ASSISTANT.OWNER.eq(owner))
        );
    }
}
