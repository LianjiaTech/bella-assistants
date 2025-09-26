package com.ke.assistant.db.repo;

import com.ke.assistant.db.IdGenerator;
import com.ke.assistant.db.generated.tables.pojos.ResponseIdMappingDb;
import com.ke.assistant.db.generated.tables.records.ResponseIdMappingRecord;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static com.ke.assistant.db.generated.Tables.RESPONSE_ID_MAPPING;

/**
 * Response ID Mapping Repository 需要生成 Response ID
 */
@Repository
@RequiredArgsConstructor
public class ResponseIdMappingRepo implements BaseRepo {

    private final DSLContext dsl;
    private final IdGenerator idGenerator;
    
    // Thread locks for concurrency control
    private final ConcurrentHashMap<String, ReentrantLock> threadLocks = new ConcurrentHashMap<>();

    public ResponseIdMappingDb findByResponseId(String responseId) {
        if (isNoStoreMode()) {
            return getContextStore().findResponseIdMappingByResponseId(responseId);
        }
        return dsl.selectFrom(RESPONSE_ID_MAPPING)
                .where(RESPONSE_ID_MAPPING.RESPONSE_ID.eq(responseId))
                .fetchOneInto(ResponseIdMappingDb.class);
    }

    public ResponseIdMappingDb findByPreviousResponseId(String responseId) {
        if (isNoStoreMode()) {
            return getContextStore().findResponseIdMappingByPreviousResponseId(responseId);
        }
        return dsl.selectFrom(RESPONSE_ID_MAPPING)
                .where(RESPONSE_ID_MAPPING.PREVIOUS_RESPONSE_ID.eq(responseId))
                .limit(1)
                .fetchOneInto(ResponseIdMappingDb.class);
    }

    public List<ResponseIdMappingDb> findByRunId(String runId) {
        if (isNoStoreMode()) {
            return getContextStore().findResponseIdMappingsByRunId(runId);
        }
        return dsl.selectFrom(RESPONSE_ID_MAPPING)
                .where(RESPONSE_ID_MAPPING.RUN_ID.eq(runId))
                .orderBy(RESPONSE_ID_MAPPING.CREATED_AT.desc())
                .fetchInto(ResponseIdMappingDb.class);
    }

    public List<ResponseIdMappingDb> findByUser(String user) {
        if (isNoStoreMode()) {
            return getContextStore().findResponseIdMappingsByUser(user);
        }
        return dsl.selectFrom(RESPONSE_ID_MAPPING)
                .where(RESPONSE_ID_MAPPING.USER.eq(user))
                .and(RESPONSE_ID_MAPPING.STATUS.eq("active"))
                .orderBy(RESPONSE_ID_MAPPING.CREATED_AT.desc())
                .fetchInto(ResponseIdMappingDb.class);
    }

    public ResponseIdMappingDb insert(ResponseIdMappingDb mapping) {
        if(StringUtils.isBlank(mapping.getResponseId())) {
            mapping.setResponseId(idGenerator.generateResponseId());
        }

        fillCreateTime(mapping);

        if (isNoStoreMode()) {
            return getContextStore().insertResponseIdMapping(mapping);
        }

        ResponseIdMappingRecord record = dsl.newRecord(RESPONSE_ID_MAPPING, mapping);
        record.store();

        return record.into(ResponseIdMappingDb.class);
    }

    public boolean update(ResponseIdMappingDb mapping) {
        fillUpdateTime(mapping);

        if (isNoStoreMode()) {
            return getContextStore().updateResponseIdMapping(mapping);
        }

        return dsl.update(RESPONSE_ID_MAPPING)
                .set(dsl.newRecord(RESPONSE_ID_MAPPING, mapping))
                .where(RESPONSE_ID_MAPPING.RESPONSE_ID.eq(mapping.getResponseId()))
                .execute() > 0;
    }

    public boolean updateStatus(String responseId, String status) {
        if (isNoStoreMode()) {
            return getContextStore().updateResponseIdMappingStatus(responseId, status);
        }
        return dsl.update(RESPONSE_ID_MAPPING)
                .set(RESPONSE_ID_MAPPING.STATUS, status)
                .set(RESPONSE_ID_MAPPING.UPDATED_AT, java.time.LocalDateTime.now())
                .where(RESPONSE_ID_MAPPING.RESPONSE_ID.eq(responseId))
                .execute() > 0;
    }

    public boolean deleteByResponseId(String responseId) {
        if (isNoStoreMode()) {
            return getContextStore().deleteResponseIdMappingByResponseId(responseId);
        }
        return dsl.deleteFrom(RESPONSE_ID_MAPPING)
                .where(RESPONSE_ID_MAPPING.RESPONSE_ID.eq(responseId))
                .execute() > 0;
    }

    public int deleteByThreadId(String threadId) {
        if (isNoStoreMode()) {
            return getContextStore().deleteResponseIdMappingsByThreadId(threadId);
        }
        return dsl.deleteFrom(RESPONSE_ID_MAPPING)
                .where(RESPONSE_ID_MAPPING.THREAD_ID.eq(threadId))
                .execute();
    }

    public boolean existsByResponseId(String responseId) {
        if (isNoStoreMode()) {
            return getContextStore().existsResponseIdMappingByResponseId(responseId);
        }
        return dsl.fetchExists(
                dsl.selectFrom(RESPONSE_ID_MAPPING)
                        .where(RESPONSE_ID_MAPPING.RESPONSE_ID.eq(responseId))
        );
    }
}
