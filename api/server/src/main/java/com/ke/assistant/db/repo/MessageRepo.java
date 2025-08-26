package com.ke.assistant.db.repo;

import com.ke.assistant.db.IdGenerator;
import com.ke.assistant.db.generated.tables.pojos.MessageDb;
import com.ke.assistant.db.generated.tables.records.MessageRecord;
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import static com.ke.assistant.db.generated.Tables.MESSAGE;

/**
 * Message Repository
 * todo: threadId 用于分表
 */
@Repository
@RequiredArgsConstructor
public class MessageRepo implements BaseRepo {

    private final DSLContext dsl;
    private final IdGenerator idGenerator;

    /**
     * 根据 ID 查询 Message
     */
    public MessageDb findById(String threadId, String id) {
        return dsl.selectFrom(MESSAGE)
                .where(MESSAGE.ID.eq(id))
                .fetchOneInto(MessageDb.class);
    }

    /**
     * 根据 ID 查询 Message
     */
    public MessageDb findByIdForUpdate(String threadId, String id) {
        return dsl.selectFrom(MESSAGE)
                .where(MESSAGE.ID.eq(id))
                .forUpdate()
                .fetchOneInto(MessageDb.class);
    }

    /**
     * 根据 Thread ID 查询 Message 列表
     */
    public List<MessageDb> findByThreadId(String threadId) {
        return dsl.selectFrom(MESSAGE)
                .where(MESSAGE.THREAD_ID.eq(threadId))
                .orderBy(MESSAGE.CREATED_AT.asc())
                .fetchInto(MessageDb.class);
    }

    /**
     * 根据 Thread ID 和 最大创建时间 查询 Message 列表
     */
    public List<MessageDb> findByThreadIdWithLimit(String threadId, LocalDateTime lessThanCreateAt) {
        return dsl.selectFrom(MESSAGE)
                .where(MESSAGE.THREAD_ID.eq(threadId))
                .and(MESSAGE.CREATED_AT.lt(lessThanCreateAt))
                .orderBy(MESSAGE.CREATED_AT.asc())
                .fetchInto(MessageDb.class);
    }

    /**
     * 根据 Run ID 查询 Message 列表
     */
    public List<MessageDb> findByRunId(String threadId, String runId) {
        return dsl.selectFrom(MESSAGE)
                .where(MESSAGE.RUN_ID.eq(runId))
                .orderBy(MESSAGE.CREATED_AT.asc())
                .fetchInto(MessageDb.class);
    }

    /**
     * 分页查询 Thread 下的 Message
     */
    public Page<MessageDb> findByThreadIdWithPage(String threadId, int page, int pageSize) {
        var query = dsl.selectFrom(MESSAGE)
                .where(MESSAGE.THREAD_ID.eq(threadId))
                .orderBy(MESSAGE.CREATED_AT.asc());

        return queryPage(dsl, query, page, pageSize, MessageDb.class);
    }

    /**
     * 插入 Message
     */
    public MessageDb insert(MessageDb message) {
        if(StringUtils.isBlank(message.getId())) {
            message.setId(idGenerator.generateMessageId());
        }

        fillCreateTime(message);

        MessageRecord record = dsl.newRecord(MESSAGE, message);
        record.store();

        return record.into(MessageDb.class);
    }

    /**
     * 更新 Message
     */
    public boolean update(MessageDb message) {
        fillUpdateTime(message);

        return dsl.update(MESSAGE)
                .set(dsl.newRecord(MESSAGE, message))
                .where(MESSAGE.ID.eq(message.getId()))
                .execute() > 0;
    }

    /**
     * 删除 Message
     */
    public boolean deleteById(String threadId, String id) {
        return dsl.deleteFrom(MESSAGE)
                .where(MESSAGE.ID.eq(id))
                .execute() > 0;
    }

    /**
     * 删除 Thread 下的所有 Message
     */
    public int deleteByThreadId(String threadId) {
        return dsl.deleteFrom(MESSAGE)
                .where(MESSAGE.THREAD_ID.eq(threadId))
                .execute();
    }
}
