package com.ke.assistant.controller;

import com.ke.assistant.model.CommonPage;
import com.ke.assistant.model.DeleteResponse;
import com.ke.assistant.db.generated.tables.pojos.ThreadDb;
import com.ke.assistant.db.repo.Page;
import com.ke.assistant.service.ThreadService;
import com.ke.assistant.util.BeanUtils;
import com.ke.assistant.util.ToolResourceUtils;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.common.exception.ResourceNotFoundException;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.assistants.thread.Thread;
import com.theokanning.openai.assistants.thread.ThreadRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {

        String owner = BellaContext.getOwnerCode();

        Page<Thread> infoPage = threadService.getThreadsByOwnerWithPage(owner, page, pageSize);

        List<Thread> infoList = infoPage.getList();

        String firstId = infoList.isEmpty() ? null : infoList.get(0).getId();
        String lastId = infoList.isEmpty() ? null : infoList.get(infoList.size() - 1).getId();
        boolean hasMore = (long) infoPage.getPage() * infoPage.getPageSize() < infoPage.getTotal();

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
}
