package com.ke.assistant.db.context;

import com.ke.assistant.db.generated.tables.pojos.MessageDb;
import com.ke.assistant.db.generated.tables.pojos.ResponseIdMappingDb;
import com.ke.assistant.db.generated.tables.pojos.RunDb;
import com.ke.assistant.db.generated.tables.pojos.RunStepDb;
import com.ke.assistant.db.generated.tables.pojos.RunToolDb;
import com.ke.assistant.db.generated.tables.pojos.ThreadDb;
import com.ke.assistant.db.generated.tables.pojos.ThreadFileRelationDb;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Thread-local repository context for non-store mode.
 * Holds in-memory maps for entities and lightweight query helpers.
 */
public class RepoContext {

    private static final ThreadLocal<State> TL = new ThreadLocal<>();

    public static boolean isActive() {
        return TL.get() != null;
    }

    public static void activate() {
        TL.set(new State());
    }

    public static void detach() {
        TL.remove();
    }

    /**
     * Capture current state reference for propagation.
     */
    public static State capture() {
        return TL.get();
    }

    /** Attach a captured state to current thread. */
    public static void attach(State state) {
        TL.set(state);
    }

    /** Auto-increment integer for tables requiring numeric id. */
    public static int nextAutoInt(String key) {
        State s = TL.get();
        if (s == null) return 0;
        return (int) s.autoIntCounters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    public static Store store() {
        State s = TL.get();
        return s == null ? null : s.store;
    }

    /** Holds the thread-local state reference. */
    public static class State {
        private final Map<String, AtomicLong> autoIntCounters = new ConcurrentHashMap<>();
        private final Store store = new Store();
    }

    /** In-memory stores and lightweight queries. */
    public static class Store {
        // entity maps
        public final Map<String, ThreadDb> threads = new ConcurrentHashMap<>();
        public final Map<Integer, ThreadFileRelationDb> threadFiles = new ConcurrentHashMap<>();
        public final Map<String, MessageDb> messages = new ConcurrentHashMap<>();
        public final Map<String, RunDb> runs = new ConcurrentHashMap<>();
        public final Map<String, RunStepDb> runSteps = new ConcurrentHashMap<>();
        public final Map<Integer, RunToolDb> runTools = new ConcurrentHashMap<>();
        public final Map<String, ResponseIdMappingDb> responseIdMappings = new ConcurrentHashMap<>();
        public final Map<String, byte[]> fileMap = new ConcurrentHashMap<>();

        // --------------- ThreadDb ops ---------------
        public ThreadDb insertThread(ThreadDb thread) {
            LocalDateTime now = LocalDateTime.now();
            thread.setCreatedAt(now);
            thread.setUpdatedAt(now);
            threads.put(thread.getId(), thread);
            return thread;
        }

        public ThreadDb findThreadById(String id) {
            return threads.get(id);
        }

        public List<ThreadDb> findThreadsByOwner(String owner) {
            return threads.values().stream()
                    .filter(t -> owner.equals(t.getOwner()))
                    .sorted(Comparator.comparing(ThreadDb::getCreatedAt).reversed())
                    .collect(Collectors.toList());
        }

        public boolean updateThread(ThreadDb thread) {
            thread.setUpdatedAt(LocalDateTime.now());
            threads.put(thread.getId(), thread);
            return true;
        }

        public boolean deleteThreadById(String id) {
            return threads.remove(id) != null;
        }

        public boolean threadExistsById(String id) {
            return threads.containsKey(id);
        }

        public boolean threadCheckOwnership(String id, String owner) {
            ThreadDb db = threads.get(id);
            return db != null && owner.equals(db.getOwner());
        }

        // --------------- ThreadFileRelationDb ops ---------------
        public ThreadFileRelationDb insertThreadFile(ThreadFileRelationDb relation) {
            LocalDateTime now = LocalDateTime.now();
            relation.setCreatedAt(now);
            relation.setUpdatedAt(now);
            if (relation.getId() == null) {
                relation.setId(RepoContext.nextAutoInt("thread_file_relation"));
            }
            threadFiles.put(relation.getId(), relation);
            return relation;
        }

        public List<ThreadFileRelationDb> findByThreadId(String threadId) {
            return threadFiles.values().stream()
                    .filter(tf -> threadId.equals(tf.getThreadId()))
                    .sorted(Comparator.comparing(ThreadFileRelationDb::getCreatedAt).reversed())
                    .collect(Collectors.toList());
        }

        public int deleteThreadFilesByThreadId(String threadId) {
            List<Integer> ids = threadFiles.values().stream()
                    .filter(tf -> threadId.equals(tf.getThreadId()))
                    .map(ThreadFileRelationDb::getId)
                    .collect(Collectors.toList());
            ids.forEach(threadFiles::remove);
            return ids.size();
        }

        // --------------- MessageDb ops ---------------
        public MessageDb insertMessage(MessageDb message) {
            LocalDateTime now = LocalDateTime.now();
            message.setCreatedAt(now);
            message.setUpdatedAt(now);
            messages.put(message.getId(), message);
            return message;
        }

        public boolean updateMessage(MessageDb message) {
            message.setUpdatedAt(LocalDateTime.now());
            messages.put(message.getId(), message);
            return true;
        }

        public MessageDb findMessageById(String id) {
            return messages.get(id);
        }

        public List<MessageDb> findMessagesByThreadId(String threadId) {
            return messages.values().stream()
                    .filter(m -> threadId.equals(m.getThreadId()))
                    .filter(m -> "original".equals(m.getMessageStatus()))
                    .sorted(Comparator.comparing(MessageDb::getCreatedAt))
                    .collect(Collectors.toList());
        }

        public List<MessageDb> findMessagesByThreadIdWithLimit(String threadId, LocalDateTime lessThanCreateAt) {
            return messages.values().stream()
                    .filter(m -> threadId.equals(m.getThreadId()))
                    .filter(m -> m.getCreatedAt().isBefore(lessThanCreateAt))
                    .filter(m -> "original".equals(m.getMessageStatus()))
                    .sorted(Comparator.comparing(MessageDb::getCreatedAt))
                    .collect(Collectors.toList());
        }

        public List<MessageDb> findMessagesByThreadIdWithIntervalIncludeHidden(String threadId, LocalDateTime from, LocalDateTime to) {
            return messages.values().stream()
                    .filter(m -> threadId.equals(m.getThreadId()))
                    .filter(m -> m.getCreatedAt().isAfter(from) && m.getCreatedAt().isBefore(to))
                    .sorted(Comparator.comparing(MessageDb::getCreatedAt))
                    .collect(Collectors.toList());
        }

        public List<MessageDb> findRecentMessagesByThreadId(String threadId, int limit) {
            return messages.values().stream()
                    .filter(m -> threadId.equals(m.getThreadId()))
                    .filter(m -> "original".equals(m.getMessageStatus()))
                    .sorted(Comparator.comparing(MessageDb::getCreatedAt).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        public int deleteMessagesByThreadId(String threadId) {
            List<String> ids = messages.values().stream()
                    .filter(m -> threadId.equals(m.getThreadId()))
                    .map(MessageDb::getId)
                    .collect(Collectors.toList());
            ids.forEach(messages::remove);
            return ids.size();
        }

        // --------------- RunDb ops ---------------
        public RunDb insertRun(RunDb run) {
            LocalDateTime now = LocalDateTime.now();
            run.setCreatedAt(now);
            run.setUpdatedAt(now);
            runs.put(run.getId(), run);
            return run;
        }

        public boolean updateRun(RunDb run) {
            run.setUpdatedAt(LocalDateTime.now());
            runs.put(run.getId(), run);
            return true;
        }

        public RunDb findRunById(String id) {
            return runs.get(id);
        }

        public List<RunDb> findRunsByThreadId(String threadId) {
            return runs.values().stream()
                    .filter(r -> threadId.equals(r.getThreadId()))
                    .sorted(Comparator.comparing(RunDb::getCreatedAt).reversed())
                    .collect(Collectors.toList());
        }

        public RunDb findAnyRunByThreadId(String threadId) {
            return runs.values().stream()
                    .filter(r -> threadId.equals(r.getThreadId()))
                    .sorted(Comparator.comparing(RunDb::getCreatedAt).reversed())
                    .findFirst().orElse(null);
        }

        // --------------- RunStepDb ops ---------------
        public RunStepDb insertRunStep(RunStepDb step) {
            LocalDateTime now = LocalDateTime.now();
            step.setCreatedAt(now);
            step.setUpdatedAt(now);
            runSteps.put(step.getId(), step);
            return step;
        }

        public boolean updateRunStep(RunStepDb step) {
            step.setUpdatedAt(LocalDateTime.now());
            runSteps.put(step.getId(), step);
            return true;
        }

        public boolean updateRunStepDetails(String id, String details) {
            RunStepDb db = runSteps.get(id);
            if (db == null) return false;
            db.setStepDetails(details);
            db.setUpdatedAt(LocalDateTime.now());
            runSteps.put(id, db);
            return true;
        }

        public RunStepDb findRunStepById(String id) {
            return runSteps.get(id);
        }

        public RunStepDb findActionRequiredForUpdate(String runId) {
            return runSteps.values().stream()
                    .filter(s -> runId.equals(s.getRunId()))
                    .filter(s -> "requires_action".equals(s.getStatus()))
                    .findAny().orElse(null);
        }

        public List<RunStepDb> findRunStepsByRunId(String threadId, String runId) {
            return runSteps.values().stream()
                    .filter(s -> runId.equals(s.getRunId()))
                    .sorted(Comparator.comparing(RunStepDb::getCreatedAt))
                    .collect(Collectors.toList());
        }

        public List<RunStepDb> findRunStepsByThreadId(String threadId) {
            return runSteps.values().stream()
                    .filter(s -> threadId.equals(s.getThreadId()))
                    .sorted(Comparator.comparing(RunStepDb::getCreatedAt))
                    .collect(Collectors.toList());
        }

        public List<RunStepDb> findRunStepsByRunIds(String threadId, List<String> runIds) {
            if (runIds == null || runIds.isEmpty()) return new ArrayList<>();
            return runSteps.values().stream()
                    .filter(s -> runIds.contains(s.getRunId()))
                    .filter(s -> threadId.equals(s.getThreadId()))
                    .sorted(Comparator.comparing(RunStepDb::getCreatedAt))
                    .collect(Collectors.toList());
        }

        // --------------- RunToolDb ops ---------------
        public RunToolDb insertRunTool(RunToolDb tool) {
            LocalDateTime now = LocalDateTime.now();
            tool.setCreatedAt(now);
            tool.setUpdatedAt(now);
            if (tool.getId() == null) {
                tool.setId(RepoContext.nextAutoInt("run_tool"));
            }
            runTools.put(tool.getId(), tool);
            return tool;
        }

        public boolean updateRunTool(RunToolDb tool) {
            tool.setUpdatedAt(LocalDateTime.now());
            runTools.put(tool.getId(), tool);
            return true;
        }

        public RunToolDb findRunToolById(Integer id) {
            return runTools.get(id);
        }

        public List<RunToolDb> findRunToolsByRunId(String runId) {
            return runTools.values().stream()
                    .filter(t -> runId.equals(t.getRunId()))
                    .sorted(Comparator.comparing(RunToolDb::getCreatedAt).reversed())
                    .collect(Collectors.toList());
        }

        public boolean existsRunToolById(Integer id) {
            return runTools.containsKey(id);
        }

        // --------------- ResponseIdMappingDb ops ---------------
        public ResponseIdMappingDb insertResponseIdMapping(ResponseIdMappingDb mapping) {
            LocalDateTime now = LocalDateTime.now();
            mapping.setCreatedAt(now);
            mapping.setUpdatedAt(now);
            responseIdMappings.put(mapping.getResponseId(), mapping);
            return mapping;
        }

        public boolean updateResponseIdMapping(ResponseIdMappingDb mapping) {
            mapping.setUpdatedAt(LocalDateTime.now());
            responseIdMappings.put(mapping.getResponseId(), mapping);
            return true;
        }

        public boolean updateResponseIdMappingStatus(String responseId, String status) {
            ResponseIdMappingDb db = responseIdMappings.get(responseId);
            if (db == null) return false;
            db.setStatus(status);
            db.setUpdatedAt(LocalDateTime.now());
            responseIdMappings.put(responseId, db);
            return true;
        }

        public ResponseIdMappingDb findResponseIdMappingByResponseId(String responseId) {
            return responseIdMappings.get(responseId);
        }

        public ResponseIdMappingDb findResponseIdMappingByPreviousResponseId(String responseId) {
            return responseIdMappings.values().stream()
                    .filter(r -> responseId.equals(r.getPreviousResponseId()))
                    .findFirst().orElse(null);
        }

        public List<ResponseIdMappingDb> findResponseIdMappingsByRunId(String runId) {
            return responseIdMappings.values().stream()
                    .filter(r -> runId.equals(r.getRunId()))
                    .sorted(Comparator.comparing(ResponseIdMappingDb::getCreatedAt).reversed())
                    .collect(Collectors.toList());
        }

        public List<ResponseIdMappingDb> findResponseIdMappingsByUser(String user) {
            return responseIdMappings.values().stream()
                    .filter(r -> user.equals(r.getUser()))
                    .filter(r -> "active".equals(r.getStatus()))
                    .sorted(Comparator.comparing(ResponseIdMappingDb::getCreatedAt).reversed())
                    .collect(Collectors.toList());
        }

        public boolean deleteResponseIdMappingByResponseId(String responseId) {
            return responseIdMappings.remove(responseId) != null;
        }

        public int deleteResponseIdMappingsByThreadId(String threadId) {
            List<String> ids = responseIdMappings.values().stream()
                    .filter(r -> threadId.equals(r.getThreadId()))
                    .map(ResponseIdMappingDb::getResponseId)
                    .collect(Collectors.toList());
            ids.forEach(responseIdMappings::remove);
            return ids.size();
        }

        public boolean existsResponseIdMappingByResponseId(String responseId) {
            return responseIdMappings.containsKey(responseId);
        }


        // --------------- File ops ---------------
        public String upload(String fileName, byte[] fileData) {
            fileMap.put(fileName, fileData);
            return fileName;
        }

        public void retrieveFileContentAndSave(String fileId, Path path) throws IOException {
            if(!fileMap.containsKey(fileId)) {
                return;
            }
            Path parentDir = path.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            try(OutputStream os = Files.newOutputStream(path)) {
                os.write(fileMap.get(fileId));
            }
        }

    }
}
