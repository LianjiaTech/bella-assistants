package com.ke.assistant.controller;

import com.ke.assistant.core.run.RunExecutor;
import com.ke.assistant.db.generated.tables.pojos.ThreadDb;
import com.ke.assistant.model.CommonPage;
import com.ke.assistant.model.DeleteResponse;
import com.ke.assistant.service.RunService;
import com.ke.assistant.service.ThreadService;
import com.ke.assistant.util.BeanUtils;
import com.ke.assistant.util.ToolResourceUtils;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.common.exception.ResourceNotFoundException;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.assistants.run.CreateThreadAndRunRequest;
import com.theokanning.openai.assistants.run.Run;
import com.theokanning.openai.assistants.run.RunCreateRequest;
import com.theokanning.openai.assistants.thread.Thread;
import com.theokanning.openai.assistants.thread.ThreadRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Thread Controller
 */
@RestController
@RequestMapping("/v1/threads")
@Slf4j
public class ThreadController {

    @Autowired
    private ThreadService threadService;
    @Autowired
    private RunService runService;
    @Autowired
    private RunExecutor runExecutor;

    /**
     * 创建 Thread
     */
    @PostMapping
    public Thread createThread(@RequestBody ThreadRequest request) {

        // transfer请求到数据库对象
        ThreadDb thread = new ThreadDb();
        BeanUtils.copyProperties(request, thread);

        thread.setOwner(BellaContext.getOwnerCode());

        // 将metadata Map转换为JSON字符串
        if(request.getMetadata() != null) {
            thread.setMetadata(JacksonUtils.serialize(request.getMetadata()));
        }

        // 将environment Map转换为JSON字符串
        if(request.getEnvironment() != null) {
            thread.setEnvironment(JacksonUtils.serialize(request.getEnvironment()));
        }

        return threadService.createThread(thread, ToolResourceUtils.toolResourceToFiles(request.getToolResources()), request.getMessages());
    }

    /**
     * 获取 Thread 详情
     */
    @GetMapping("/{thread_id}")
    public Thread getThread(@PathVariable("thread_id") String threadId) {

        Thread info = threadService.getThreadById(threadId);
        if(info == null) {
            throw new ResourceNotFoundException("Thread not found");
        }
        return info;
    }

    /**
     * 获取 Thread 列表
     */
    @GetMapping
    public CommonPage<Thread> listThreads(
            @RequestParam(value = "after", required = false) String after,
            @RequestParam(value = "before", required = false) String before,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "order", defaultValue = "desc") String order) {

        String owner = BellaContext.getOwnerCode();

        List<Thread> infoList = threadService.getThreadsByCursor(owner, after, before, limit + 1, order);

        boolean hasMore = infoList.size() > limit;
        if (hasMore) {
            infoList.remove(infoList.size() - 1);
        }

        String firstId = infoList.isEmpty() ? null : infoList.get(0).getId();
        String lastId = infoList.isEmpty() ? null : infoList.get(infoList.size() - 1).getId();

        return new CommonPage<>(infoList, firstId, lastId, hasMore);
    }

    /**
     * 更新 Thread
     */
    @PostMapping("/{thread_id}")
    public Thread updateThread(
            @PathVariable("thread_id") String threadId,
            @RequestBody ThreadRequest request) {

        // transfer请求到数据库对象
        ThreadDb updateData = new ThreadDb();
        BeanUtils.copyProperties(request, updateData);

        // 将metadata Map转换为JSON字符串
        if(request.getMetadata() != null) {
            updateData.setMetadata(JacksonUtils.serialize(request.getMetadata()));
        }

        // 将environment Map转换为JSON字符串
        if(request.getEnvironment() != null) {
            updateData.setEnvironment(JacksonUtils.serialize(request.getEnvironment()));
        }

        String owner = BellaContext.getOwnerCode();

        return threadService.updateThread(threadId, updateData, ToolResourceUtils.toolResourceToFiles(request.getToolResources()), owner);
    }

    /**
     * 删除 Thread
     */
    @DeleteMapping("/{thread_id}")
    public DeleteResponse deleteThread(
            @PathVariable("thread_id") String threadId) {

        String owner = BellaContext.getOwnerCode();
        boolean deleted = threadService.deleteThread(threadId, owner);
        return new DeleteResponse(threadId, "thread", deleted);
    }

    /**
     * Fork Thread - 复制Thread及其消息
     */
    @PostMapping("/{thread_id}/fork")
    public Thread forkThread(@PathVariable("thread_id") String threadId) {

        return threadService.forkThread(threadId);
    }

    /**
     * 复制Thread消息到另一个Thread
     */
    @PostMapping("/{from_thread_id}/copy_to/{to_thread_id}")
    public Thread copyThread(
            @PathVariable("from_thread_id") String fromThreadId,
            @PathVariable("to_thread_id") String toThreadId) {

        return threadService.copyThread(fromThreadId, toThreadId);
    }

    /**
     * 合并Thread消息到另一个Thread
     */
    @PostMapping("/{from_thread_id}/merge_to/{to_thread_id}")
    public Thread mergeThread(
            @PathVariable("from_thread_id") String fromThreadId,
            @PathVariable("to_thread_id") String toThreadId) {

        return threadService.mergeThread(fromThreadId, toThreadId);
    }

    /**
     * 创建 Thread 和 Run
     */
    @PostMapping("/runs")
    public Object createThreadAndRun(@RequestBody CreateThreadAndRunRequest request) {

        Thread thread = createThread(request.getThread());

        RunCreateRequest runCreateRequest = new RunCreateRequest();

        BeanUtils.copyProperties(request, runCreateRequest);

        // 创建Thread和Run
        Pair<Run, String> pair = runService.createRun(thread.getId(), runCreateRequest);

        SseEmitter emitter = null;
        // 如果是流式请求，返回SseEmitter
        if(Boolean.TRUE.equals(request.getStream())) {
            emitter = new SseEmitter(300000L); // 5分钟超时
        }
        runExecutor.startRun(thread.getId(), pair.getLeft().getId(), pair.getRight(), true, emitter);
        return Boolean.TRUE.equals(request.getStream()) ? emitter : pair.getLeft();
    }
}
