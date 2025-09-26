package com.ke.assistant.db.repo;

import com.ke.assistant.db.IdGenerator;
import com.ke.assistant.db.context.RepoContext;
import com.ke.assistant.db.generated.tables.pojos.MessageDb;
import com.ke.assistant.db.generated.tables.records.MessageRecord;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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
        
        if (isNoStoreMode()) {
            return getContextStore().findMessageById(id);
        }
        return dsl.selectFrom(MESSAGE)
                .where(MESSAGE.ID.eq(id))
                .fetchOneInto(MessageDb.class);
    }

    /**
     * 根据 ID 查询 Message
     */
    public MessageDb findByIdForUpdate(String threadId, String id) {
        
        if (isNoStoreMode()) {
            // Non-store mode doesn't need DB-level locks
            return getContextStore().findMessageById(id);
        }
        return dsl.selectFrom(MESSAGE)
                .where(MESSAGE.ID.eq(id))
                .forUpdate()
                .fetchOneInto(MessageDb.class);
    }

    /**
     * 根据 Thread ID 查询 Message 列表
     */
    public List<MessageDb> findByThreadId(String threadId) {
        
        if (isNoStoreMode()) {
            return getContextStore().findMessagesByThreadId(threadId);
        }
        return dsl.selectFrom(MESSAGE)
                .where(MESSAGE.THREAD_ID.eq(threadId))
                .and(MESSAGE.MESSAGE_STATUS.eq("original"))
                .orderBy(MESSAGE.CREATED_AT.asc())
                .fetchInto(MessageDb.class);
    }

    /**
     * 根据 Thread ID 和 最大创建时间 查询 Message 列表
     */
    public List<MessageDb> findByThreadIdWithLimit(String threadId, LocalDateTime lessThanCreateAt) {
        
        if (isNoStoreMode()) {
            return getContextStore().findMessagesByThreadIdWithLimit(threadId, lessThanCreateAt);
        }
        return dsl.selectFrom(MESSAGE)
                .where(MESSAGE.THREAD_ID.eq(threadId))
                .and(MESSAGE.CREATED_AT.lessThan(lessThanCreateAt))
                .and(MESSAGE.MESSAGE_STATUS.eq("original"))
                .orderBy(MESSAGE.CREATED_AT.asc())
                .fetchInto(MessageDb.class);
    }

    /**
     * 根据 Thread ID 和 创建时间间隔查询
     */
    public List<MessageDb> findByThreadIdWithIntervalIncludeHidden(String threadId, LocalDateTime from, LocalDateTime to) {
        
        if (isNoStoreMode()) {
            return getContextStore().findMessagesByThreadIdWithIntervalIncludeHidden(threadId, from, to);
        }
        return dsl.selectFrom(MESSAGE)
                .where(MESSAGE.THREAD_ID.eq(threadId))
                .and(MESSAGE.CREATED_AT.greaterThan(from))
                .and(MESSAGE.CREATED_AT.lessThan(to))
                .orderBy(MESSAGE.CREATED_AT.asc())
                .fetchInto(MessageDb.class);
    }


    /**
     * 基于游标的分页查询 Thread 下的 Message
     */
    public List<MessageDb> findByThreadIdWithCursor(String threadId, String after, String before, int limit, String order) {
        
        if (isNoStoreMode()) {
            // Simplified in-memory pagination by createdAt
            List<MessageDb> all = getContextStore().findMessagesByThreadId(threadId);
            if (after != null && !after.isEmpty()) {
                MessageDb afterMsg = getContextStore().findMessageById(after);
                if (afterMsg != null) {
                    all = all.stream().filter(m -> m.getCreatedAt().isAfter(afterMsg.getCreatedAt())).collect(Collectors.toList());
                }
            }
            if (before != null && !before.isEmpty()) {
                MessageDb beforeMsg = getContextStore().findMessageById(before);
                if (beforeMsg != null) {
                    all = all.stream().filter(m -> m.getCreatedAt().isBefore(beforeMsg.getCreatedAt())).collect(Collectors.toList());
                }
            }
            Comparator<MessageDb> cmp = Comparator.comparing(MessageDb::getCreatedAt);
            if (!"asc".equalsIgnoreCase(order)) {
                cmp = cmp.reversed();
            }
            return all.stream().sorted(cmp).limit(limit).collect(Collectors.toList());
        }
        return findWithCursor(
                dsl,
                MESSAGE,
                MESSAGE.THREAD_ID.eq(threadId).and(MESSAGE.MESSAGE_STATUS.eq("original")),
                MESSAGE.CREATED_AT,
                after,
                before,
                limit,
                order,
                id -> findById(threadId, id),
                MessageDb.class
        );
    }

    /**
     * 插入 Message
     */
    public MessageDb insert(MessageDb message) {
        message.setObject("thread.message");
        if(StringUtils.isBlank(message.getId())) {
            message.setId(idGenerator.generateMessageId());
        }

        if (isNoStoreMode()) {
            fillCreateTime(message);
            return getContextStore().insertMessage(message);
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
        
        if (isNoStoreMode()) {
            fillUpdateTime(message);
            return getContextStore().updateMessage(message);
        }
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
        
        if (isNoStoreMode()) {
            return getContextStore().messages.remove(id) != null;
        }
        return dsl.deleteFrom(MESSAGE)
                .where(MESSAGE.ID.eq(id))
                .execute() > 0;
    }

    /**
     * 根据 Thread ID 获取最近的消息（倒序）
     */
    public List<MessageDb> findRecentByThreadId(String threadId, int limit) {
        
        if (isNoStoreMode()) {
            return getContextStore().findRecentMessagesByThreadId(threadId, limit);
        }
        return dsl.selectFrom(MESSAGE)
                .where(MESSAGE.THREAD_ID.eq(threadId))
                .and(MESSAGE.MESSAGE_STATUS.eq("original"))
                .orderBy(MESSAGE.CREATED_AT.desc())
                .limit(limit)
                .fetchInto(MessageDb.class);
    }

    /**
     * 删除 Thread 下的所有 Message
     */
    public int deleteByThreadId(String threadId) {
        
        if (isNoStoreMode()) {
            return getContextStore().deleteMessagesByThreadId(threadId);
        }
        return dsl.deleteFrom(MESSAGE)
                .where(MESSAGE.THREAD_ID.eq(threadId))
                .execute();
    }
}
