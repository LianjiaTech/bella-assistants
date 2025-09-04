package com.ke.assistant.db.repo;

import com.ke.assistant.db.generated.tables.pojos.ThreadFileRelationDb;
import com.ke.assistant.db.generated.tables.records.ThreadFileRelationRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ke.assistant.db.generated.Tables.THREAD_FILE_RELATION;

/**
 * Thread File Relation Repository 自增 ID，不需要生成
 */
@Repository
@RequiredArgsConstructor
public class ThreadFileRelationRepo implements BaseRepo {

    private final DSLContext dsl;

    public ThreadFileRelationDb findById(Integer id) {
        return dsl.selectFrom(THREAD_FILE_RELATION)
                .where(THREAD_FILE_RELATION.ID.eq(id))
                .fetchOneInto(ThreadFileRelationDb.class);
    }

    public List<ThreadFileRelationDb> findByThreadId(String threadId) {
        return dsl.selectFrom(THREAD_FILE_RELATION)
                .where(THREAD_FILE_RELATION.THREAD_ID.eq(threadId))
                .orderBy(THREAD_FILE_RELATION.CREATED_AT.desc())
                .fetchInto(ThreadFileRelationDb.class);
    }

    public List<ThreadFileRelationDb> findByFileId(String fileId) {
        return dsl.selectFrom(THREAD_FILE_RELATION)
                .where(THREAD_FILE_RELATION.FILE_ID.eq(fileId))
                .orderBy(THREAD_FILE_RELATION.CREATED_AT.desc())
                .fetchInto(ThreadFileRelationDb.class);
    }

    public ThreadFileRelationDb findByUniqueKey(String fileId, String threadId, String toolName) {
        return dsl.selectFrom(THREAD_FILE_RELATION)
                .where(THREAD_FILE_RELATION.FILE_ID.eq(fileId))
                .and(THREAD_FILE_RELATION.THREAD_ID.eq(threadId))
                .and(THREAD_FILE_RELATION.TOOL_NAME.eq(toolName))
                .fetchOneInto(ThreadFileRelationDb.class);
    }

    public ThreadFileRelationDb insert(ThreadFileRelationDb relation) {
        relation.setObject("thread.file");
        // 自增 ID，不需要设置
        fillCreateTime(relation);

        ThreadFileRelationRecord record = dsl.newRecord(THREAD_FILE_RELATION, relation);
        record.store();

        return record.into(ThreadFileRelationDb.class);
    }

    public boolean update(ThreadFileRelationDb relation) {
        fillUpdateTime(relation);

        return dsl.update(THREAD_FILE_RELATION)
                .set(dsl.newRecord(THREAD_FILE_RELATION, relation))
                .where(THREAD_FILE_RELATION.ID.eq(relation.getId()))
                .execute() > 0;
    }

    public boolean deleteById(Integer id) {
        return dsl.deleteFrom(THREAD_FILE_RELATION)
                .where(THREAD_FILE_RELATION.ID.eq(id))
                .execute() > 0;
    }

    public boolean deleteByUniqueKey(String fileId, String threadId, String toolName) {
        return dsl.deleteFrom(THREAD_FILE_RELATION)
                .where(THREAD_FILE_RELATION.FILE_ID.eq(fileId))
                .and(THREAD_FILE_RELATION.THREAD_ID.eq(threadId))
                .and(THREAD_FILE_RELATION.TOOL_NAME.eq(toolName))
                .execute() > 0;
    }

    public int deleteByThreadId(String threadId) {
        return dsl.deleteFrom(THREAD_FILE_RELATION)
                .where(THREAD_FILE_RELATION.THREAD_ID.eq(threadId))
                .execute();
    }

    public boolean existsById(Integer id) {
        return dsl.fetchExists(
                dsl.selectFrom(THREAD_FILE_RELATION)
                        .where(THREAD_FILE_RELATION.ID.eq(id))
        );
    }

    public boolean existsByUniqueKey(String fileId, String threadId, String toolName) {
        return dsl.fetchExists(
                dsl.selectFrom(THREAD_FILE_RELATION)
                        .where(THREAD_FILE_RELATION.FILE_ID.eq(fileId))
                        .and(THREAD_FILE_RELATION.THREAD_ID.eq(threadId))
                        .and(THREAD_FILE_RELATION.TOOL_NAME.eq(toolName))
        );
    }
}
