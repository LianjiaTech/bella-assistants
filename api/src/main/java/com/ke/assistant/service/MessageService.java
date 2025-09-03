package com.ke.assistant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.assistant.db.generated.tables.pojos.MessageDb;
import com.ke.assistant.db.repo.MessageRepo;
import com.ke.assistant.util.BeanUtils;
import com.ke.assistant.util.MessageUtils;
import com.ke.bella.openapi.common.exception.ResourceNotFoundException;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.assistants.message.Message;
import com.theokanning.openai.assistants.message.MessageContent;
import com.theokanning.openai.assistants.message.MessageRequest;
import com.theokanning.openai.assistants.thread.Attachment;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Message Service
 */
@Service
@Slf4j
public class MessageService {

    @Autowired
    private MessageRepo messageRepo;

    @Autowired
    @Lazy
    private ThreadService threadService;
    
    @Autowired
    private ThreadLockService threadLockService;

    /**
     * 创建 Message（单条插入使用读锁，允许并发执行）
     */
    @Transactional
    public Message createMessage(String threadId, MessageRequest request) {
        return createMessage(threadId, request, "completed", false);
    }

    @Transactional
    public Message createRunStepMessage(String threadId, MessageRequest request) {
        return createMessage(threadId, request, "in_progress", true);
    }

    @Transactional
    public Message createMessage(String threadId, MessageRequest request, String status, boolean hidden) {
        // 验证Thread是否存在
        if (!threadService.existsById(threadId)) {
            throw new ResourceNotFoundException("Thread not found: " + threadId);
        }

        MessageDb message = new MessageDb();
        BeanUtils.copyProperties(request, message);
        message.setThreadId(threadId);
        message.setStatus(status);
        message.setMessageStatus(hidden ? "hidden" : "original");

        // 格式化content内容
        List<Object> formattedContent = MessageUtils.formatMessageContent(request.getContent());
        message.setContent(JacksonUtils.serialize(formattedContent));

        // 序列化其他复杂字段
        if(request.getAttachments() != null) {
            message.setAttachments(JacksonUtils.serialize(request.getAttachments()));
        }
        if(request.getMetadata() != null) {
            message.setMetadata(JacksonUtils.serialize(request.getMetadata()));
        }

        //设置消息类型
        message.setMessageType(MessageUtils.recognizeMessageType(request.getContent()));

        // 设置默认值
        message.setObject("thread.message");

        // 只有实际的数据库插入操作需要加读锁
        MessageDb savedMessage = threadLockService.executeWithReadLock(threadId, () -> messageRepo.insert(message));

        return convertToInfo(savedMessage);
    }

    /**
     * 根据ID获取Message
     */
    public Message getMessageById(String threadId, String id) {
        MessageDb messageDb = messageRepo.findById(threadId, id);
        return messageDb != null ? convertToInfo(messageDb) : null;
    }

    /**
     * 根据Thread ID查询MessageDb列表
     */
    public List<MessageDb> getMessageDbsByThreadId(String threadId) {
        return messageRepo.findByThreadId(threadId);
    }

    /**
     * 根据Thread ID查询MessageDb列表，用于run
     * 获取运行相关的消息列表，并可选择根据命令类型进行过滤
     */
    public List<Message> getMessagesForRun(String threadId, LocalDateTime runCreateAt) {
        List<MessageDb> messages = messageRepo.findByThreadIdWithLimitWithoutHidden(threadId, runCreateAt);

        // 从后向前查找第一个命令类型的消息
        for (int i = messages.size() - 1; i >= 0; i--) {
            MessageDb message = messages.get(i);
            if ("command".equals(message.getMessageType())) {
                // 解析内容获取命令类型
                try {
                    List<Map<String, Object>> contentList = JacksonUtils.deserialize(
                        message.getContent(),
                        new TypeReference<List<Map<String, Object>>>() {}
                    );
                    if (!contentList.isEmpty()) {
                        String commandType = (String) contentList.get(0).get("type");
                        // 如果是clear命令，只返回该命令之后的消息
                        if ("clear".equals(commandType)) {
                            return messages.subList(i + 1, messages.size()).stream().map(this::convertToInfo).collect(Collectors.toList());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse command message content: {}", message.getContent(), e);
                }
            }
        }

        // 如果不需要过滤或没有找到clear命令，返回完整消息列表
        return messages.stream().map(this::convertToInfo).collect(Collectors.toList());
    }


    /**
     * 基于游标的分页查询Thread下的Message
     */
    public List<Message> getMessagesByCursor(String threadId, String after, String before, int limit, String order) {
        // 验证Thread是否存在
        if (!threadService.existsById(threadId)) {
            throw new ResourceNotFoundException("Thread not found: " + threadId);
        }
        List<MessageDb> messages = messageRepo.findByThreadIdWithCursor(threadId, after, before, limit, order);
        return messages.stream().map(this::convertToInfo).collect(Collectors.toList());
    }

    /**
     * 更新Message
     */
    @Transactional
    public Message updateMessage(String threadId, String id, MessageRequest request) {
        MessageDb existing = messageRepo.findById(threadId, id);
        if(existing == null) {
            throw new IllegalArgumentException("Message not found: " + id);
        }

        MessageDb updateData = new MessageDb();
        BeanUtils.copyProperties(request, updateData);

        // 格式化content内容
        List<Object> formattedContent = MessageUtils.formatMessageContent(request.getContent());
        updateData.setContent(JacksonUtils.serialize(formattedContent));
        //设置消息类型
        updateData.setMessageType(MessageUtils.recognizeMessageType(request.getContent()));

        // 序列化metadata
        if(request.getMetadata() != null) {
            updateData.setMetadata(JacksonUtils.serialize(request.getMetadata()));
        }

        BeanUtils.copyNonNullProperties(updateData, existing);

        messageRepo.update(existing);
        return convertToInfo(existing);
    }

    /**
     * 更新Message的内容 + reasoning
     */
    @Transactional
    public Message addContent(String threadId, String id, MessageContent content, String reasoning) {
        MessageDb existing = messageRepo.findByIdForUpdate(threadId, id);
        if(existing == null) {
            throw new IllegalArgumentException("Message not found: " + id);
        }

        List<MessageContent> contents = JacksonUtils.deserialize(existing.getContent(), new TypeReference<List<MessageContent>>() {});

        if(contents == null) {
            contents = new ArrayList<>();
        }

        if(contents.isEmpty()) {
            contents.add(content);
        } else {
            MessageContent last = contents.get(contents.size() - 1);
            if(last.empty()) {
                contents.set(contents.size() - 1, content);
            } else {
                contents.add(content);
            }
        }

        existing.setContent(JacksonUtils.serialize(contents));

        if(StringUtils.isNotBlank(reasoning)) {
            existing.setReasoningContent(reasoning);
        }

        // 修改内容可能会影响MessageType
        existing.setMessageType(MessageUtils.recognizeMessageType(existing.getContent()));

        messageRepo.update(existing);
        return convertToInfo(existing);
    }

    @Transactional
    public Message updateStatus(String threadId, String id, String status, boolean hidden) {
        MessageDb existing = messageRepo.findByIdForUpdate(threadId, id);
        if(existing == null) {
            throw new IllegalArgumentException("Message not found: " + id);
        }

        existing.setStatus(status);

        existing.setMessageStatus(hidden ? "hidden" : "original");

        messageRepo.update(existing);
        return convertToInfo(existing);
    }

    /**
     * 删除Message
     */
    @Transactional
    public boolean deleteMessage(String threadId, String id) {
        return messageRepo.deleteById(threadId, id);
    }

    /**
     * 删除Thread下的所有Message
     */
    @Transactional
    public int deleteMessagesByThreadId(String threadId) {
        return messageRepo.deleteByThreadId(threadId);
    }

    /**
     * 将MessageDb转换为MessageInfo
     */
    private Message convertToInfo(MessageDb messageDb) {
        if(messageDb == null) {
            return null;
        }

        Message info = new Message();
        BeanUtils.copyProperties(messageDb, info);

        info.setCreatedAt((int) messageDb.getCreatedAt().toEpochSecond(ZoneOffset.ofHours(8)));

        // 转换metadata从JSON字符串到Map
        if(StringUtils.isNotBlank(messageDb.getMetadata())) {
            info.setMetadata(JacksonUtils.toMap(messageDb.getMetadata()));
        }

        // content字段处理 - 数据库存储的是Content对象数组的JSON
        if(StringUtils.isNotBlank(messageDb.getContent())) {
            // 数据库中存储的格式：[{"type": "text", "text": {"value": "内容", "annotations": []}}]
            info.setContent(JacksonUtils.deserialize(messageDb.getContent(), new TypeReference<List<MessageContent>>() {
            }));
        }

        // attachments字段反序列化
        if(StringUtils.isNotBlank(messageDb.getAttachments())) {
            info.setAttachments(JacksonUtils.deserialize(messageDb.getAttachments(), new TypeReference<List<Attachment>>() {
            }));
        }

        return info;
    }

    /**
     * 直接保存MessageDb，供ThreadService的复制操作使用（不加锁，用于批量操作内部调用）
     */
    @Transactional
    public void createMessage(MessageDb message) {
        // 设置默认值
        message.setObject("thread.message");
        
        // 批量操作已经持有写锁，这里不需要再加锁
        messageRepo.insert(message);
    }

}
