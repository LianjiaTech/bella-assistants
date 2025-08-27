package com.ke.assistant.service;

import com.ke.assistant.db.generated.tables.pojos.MessageDb;
import com.ke.assistant.db.generated.tables.pojos.ThreadDb;
import com.ke.assistant.db.generated.tables.pojos.ThreadFileRelationDb;
import com.ke.assistant.db.repo.MessageRepo;
import com.ke.assistant.db.repo.ThreadFileRelationRepo;
import com.ke.assistant.db.repo.ThreadRepo;
import com.ke.assistant.util.BeanUtils;
import com.ke.assistant.util.MessageUtils;
import com.ke.assistant.util.ToolResourceUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.assistants.message.MessageRequest;
import com.theokanning.openai.assistants.thread.Thread;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thread Service
 */
@Service
@Slf4j
public class ThreadService {

    @Autowired
    private ThreadRepo threadRepo;
    @Autowired
    private ThreadFileRelationRepo threadFileRepo;
    @Autowired
    private MessageRepo messageRepo;
    @Autowired
    private MessageService messageService;
    @Autowired
    private ThreadLockService threadLockService;

    /**
     * 创建 Thread
     */
    @Transactional
    public Thread createThread(ThreadDb thread, List<Map<String, String>> toolResources, List<MessageRequest> messageOps) {
        // 设置默认值
        thread.setObject("thread");
        if(StringUtils.isBlank(thread.getEnvironment())) {
            thread.setEnvironment("{}");
        }
        if(StringUtils.isBlank(thread.getMetadata())) {
            thread.setMetadata("{}");
        }

        ThreadDb savedThread = threadRepo.insert(thread);

        // 处理文件关联
        updateThreadFilesFromToolResources(savedThread.getId(), toolResources);

        // 处理初始消息
        if(messageOps != null && !messageOps.isEmpty()) {
            for (MessageRequest messageOp : messageOps) {
                messageService.createMessage(savedThread.getId(), messageOp);
            }
        }

        return convertToInfo(savedThread);
    }

    /**
     * 根据ID获取Thread
     */
    public Thread getThreadById(String id) {
        ThreadDb threadDb = threadRepo.findById(id);
        return threadDb != null ? convertToInfo(threadDb) : null;
    }

    /**
     * 检查Thread所有权
     */
    public boolean checkOwnership(String id, String owner) {
        return threadRepo.checkOwnership(id, owner);
    }

    /**
     * 根据owner查询Thread列表
     */
    public List<Thread> getThreadsByOwner(String owner) {
        List<ThreadDb> threads = threadRepo.findByOwner(owner);
        return threads.stream().map(this::convertToInfo).collect(java.util.stream.Collectors.toList());
    }


    /**
     * 基于游标的分页查询Thread
     */
    public List<Thread> getThreadsByCursor(String owner, String after, String before, int limit, String order) {
        List<ThreadDb> threads = threadRepo.findByOwnerWithCursor(owner, after, before, limit, order);
        return threads.stream().map(this::convertToInfo).collect(java.util.stream.Collectors.toList());
    }

    /**
     * 更新Thread
     */
    @Transactional
    public Thread updateThread(String id, ThreadDb updateData, List<Map<String, String>> toolResources, String owner) {
        ThreadDb existing = threadRepo.findById(id);
        if(existing == null) {
            throw new IllegalArgumentException("Thread not found: " + id);
        }

        // 权限检查在Service中进行
        if(!checkOwnership(id, owner)) {
            throw new IllegalArgumentException("Permission denied");
        }

        existing.setObject("thread");
        if(StringUtils.isBlank(existing.getEnvironment())) {
            existing.setEnvironment("{}");
        }
        if(StringUtils.isBlank(existing.getMetadata())) {
            existing.setMetadata("{}");
        }

        BeanUtils.copyNonNullProperties(updateData, existing);

        threadRepo.update(existing);

        // 更新文件关联
        if(toolResources != null) {
            updateThreadFilesFromToolResources(id, toolResources);
        }

        return convertToInfo(existing);
    }

    /**
     * 删除Thread
     */
    @Transactional
    public boolean deleteThread(String id, String owner) {
        // 权限检查在Service中进行
        if(!checkOwnership(id, owner)) {
            throw new IllegalArgumentException("Permission denied");
        }

        // 删除关联的文件
        threadFileRepo.deleteByThreadId(id);
        // 删除关联的消息
        messageRepo.deleteByThreadId(id);
        // 删除Thread本身
        return threadRepo.deleteById(id);
    }

    /**
     * 检查Thread是否存在
     */
    public boolean existsById(String id) {
        return threadRepo.existsById(id);
    }

    /**
     * 获取Thread的文件列表
     */
    public List<ThreadFileRelationDb> getThreadFiles(String threadId) {
        return threadFileRepo.findByThreadId(threadId);
    }

    /**
     * Fork一个Thread - 复制Thread及其消息
     */
    @Transactional
    public Thread forkThread(String threadId) {
        ThreadDb originalThread = threadRepo.findById(threadId);
        if(originalThread == null) {
            throw new IllegalArgumentException("Thread not found: " + threadId);
        }

        // 创建新的Thread
        ThreadDb newThread = new ThreadDb();
        BeanUtils.copyProperties(originalThread, newThread);
        newThread.setId(null); // 重新生成ID

        ThreadDb savedThread = threadRepo.insert(newThread);

        // 复制文件关联
        List<ThreadFileRelationDb> files = getThreadFiles(threadId);
        for (ThreadFileRelationDb file : files) {
            ThreadFileRelationDb newFile = new ThreadFileRelationDb();
            BeanUtils.copyProperties(file, newFile);
            newFile.setId(null);
            newFile.setThreadId(savedThread.getId());
            threadFileRepo.insert(newFile);
        }

        // 复制消息
        copyMessagesFromThread(threadId, savedThread.getId());

        return convertToInfo(savedThread);
    }

    /**
     * 复制Thread消息到另一个Thread
     */
    @Transactional
    public Thread copyThread(String fromThreadId, String toThreadId) {
        ThreadDb fromThread = threadRepo.findById(fromThreadId);
        ThreadDb toThread = threadRepo.findById(toThreadId);

        if(fromThread == null) {
            throw new IllegalArgumentException("Source thread not found: " + fromThreadId);
        }
        if(toThread == null) {
            throw new IllegalArgumentException("Target thread not found: " + toThreadId);
        }

        // 删除目标线程现有消息
        messageService.deleteMessagesByThreadId(toThreadId);

        // 复制消息
        copyMessagesFromThread(fromThreadId, toThreadId);

        return convertToInfo(toThread);
    }

    /**
     * 合并Thread消息到另一个Thread
     */
    @Transactional
    public Thread mergeThread(String fromThreadId, String toThreadId) {
        // 智能合并消息（避免重复）
        mergeMessagesFromThread(fromThreadId, toThreadId);

        ThreadDb toThread = threadRepo.findById(toThreadId);
        return convertToInfo(toThread);
    }

    /**
     * 更新Thread的文件关联从tool_resources
     */
    @Transactional
    public void updateThreadFilesFromToolResources(String threadId, List<Map<String, String>> toolResources) {
        // 删除现有的文件关联
        threadFileRepo.deleteByThreadId(threadId);

        if(toolResources != null) {
            for (Map<String, String> toolResource : toolResources) {
                ThreadFileRelationDb threadFile = new ThreadFileRelationDb();
                threadFile.setFileId(toolResource.get("file_id"));
                threadFile.setThreadId(threadId);
                threadFile.setObject("thread.file");
                threadFile.setToolName(toolResource.get("tool_name"));
                threadFileRepo.insert(threadFile);
            }
        }
    }

    /**
     * 将ThreadDb转换为ThreadInfo
     */
    @SuppressWarnings("unchecked")
    private Thread convertToInfo(ThreadDb threadDb) {
        if(threadDb == null) {
            return null;
        }

        Thread info = new Thread();
        // 进行基础字段拷贝
        BeanUtils.copyProperties(threadDb, info);

        info.setCreatedAt((int) threadDb.getCreatedAt().toEpochSecond(ZoneOffset.ofHours(8)));

        // 转换metadata从JSON字符串到Map
        if(StringUtils.isNotBlank(threadDb.getMetadata())) {
            info.setMetadata(JacksonUtils.toMap(threadDb.getMetadata()));
        }

        // 转换environment从JSON字符串到Map
        if(StringUtils.isNotBlank(threadDb.getEnvironment())) {
            info.setEnvironment(JacksonUtils.toMap(threadDb.getEnvironment()));
        }

        // 设置关联数据
        List<ThreadFileRelationDb> files = getThreadFiles(threadDb.getId());

        // 计算tool_resources
        List<Map<String, String>> toolResources = new ArrayList<>();
        for (ThreadFileRelationDb file : files) {
            Map<String, String> toolResource = new HashMap<>();
            toolResource.put("file_id", file.getFileId());
            toolResource.put("tool_name", file.getToolName());
            toolResources.add(toolResource);
        }
        info.setToolResources(ToolResourceUtils.buildToolResourcesFromFiles(toolResources));

        return info;
    }

    /**
     * 复制消息从一个Thread到另一个Thread
     */
    @Transactional
    public void copyMessagesFromThread(String fromThreadId, String toThreadId) {
        threadLockService.executeWithWriteLock(toThreadId, () -> {
            List<MessageDb> sourceMessages = messageService.getMessageDbsByThreadId(fromThreadId);

            for (MessageDb sourceMessage : sourceMessages) {
                // 使用MessageUtils复制消息
                MessageDb newMessage = MessageUtils.copyMessageToThread(sourceMessage, toThreadId);
                messageService.createMessage(newMessage);
            }
        });
    }

    /**
     * 智能合并消息（避免重复）
     */
    @Transactional
    public void mergeMessagesFromThread(String fromThreadId, String toThreadId) {
        threadLockService.executeWithWriteLock(toThreadId, () -> {
            List<MessageDb> fromMessages = messageService.getMessageDbsByThreadId(fromThreadId);
            List<MessageDb> toMessages = messageService.getMessageDbsByThreadId(toThreadId);

            // 使用MessageUtils获取目标线程中已存在的源消息ID
            Set<String> existingSourceIds = MessageUtils.getExistingSourceIds(toMessages);

            // 只复制不重复的消息
            for (MessageDb fromMessage : fromMessages) {
                // 使用MessageUtils检查消息是否已存在
                if (!MessageUtils.isMessageExists(fromMessage, existingSourceIds)) {
                    // 使用MessageUtils复制消息
                    MessageDb newMessage = MessageUtils.copyMessageToThread(fromMessage, toThreadId);
                    messageService.createMessage(newMessage);
                }
            }
        });
    }
}
