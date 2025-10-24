package com.ke.assistant.service;

import static com.ke.assistant.util.MessageUtils.convertToInfo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.assistant.db.generated.tables.pojos.MessageDb;
import com.ke.assistant.db.repo.MessageRepo;
import com.ke.assistant.util.BeanUtils;
import com.ke.assistant.util.MessageUtils;
import com.ke.assistant.util.MetaConstants;
import com.ke.bella.openapi.common.exception.ResourceNotFoundException;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.assistants.message.IncompleteDetails;
import com.theokanning.openai.assistants.message.Message;
import com.theokanning.openai.assistants.message.MessageContent;
import com.theokanning.openai.assistants.message.MessageRequest;

import lombok.extern.slf4j.Slf4j;

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
        return createMessage(threadId, request, "completed", false, null);
    }

    @Transactional
    public Message createMessage(String threadId, MessageRequest request, Message pre) {
        return createMessage(threadId, request, "completed", false, pre);
    }

    @Transactional
    public Message createRunStepMessage(String threadId, MessageRequest request) {
        return createMessage(threadId, request, "in_progress", true, null);
    }

    @Transactional
    public Message createMessage(String threadId, MessageRequest request, String status, boolean hidden, Message pre) {

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
        } else {
            message.setAttachments("[]");
        }

        if(request.getMetadata() != null) {
            message.setMetadata(JacksonUtils.serialize(request.getMetadata()));
        } else {
            message.setMetadata("{}");
        }

        //设置消息类型
        message.setMessageType(MessageUtils.recognizeMessageType(request.getContent()));

        // 设置默认值
        message.setObject("thread.message");

        MessageUtils.checkPre(pre, convertToInfo(message));

        // 只有实际的数据库插入操作需要加读锁
        MessageDb savedMessage = threadLockService.executeWithReadLock(threadId, () -> messageRepo.insert(message));

        return convertToInfo(savedMessage);
    }


    @Transactional
    public Message createMessage(String threadId, Message message, boolean hidden) {
        MessageDb db = convertToDb(message);
        db.setThreadId(threadId);
        db.setMessageStatus(hidden ? "hidden" : "original");

        // 只有实际的数据库插入操作需要加读锁
        MessageDb savedMessage = threadLockService.executeWithReadLock(threadId, () -> messageRepo.insert(db));

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
     * 根据ID获取MessageDb
     */
    public MessageDb getMessageDbById(String threadId, String id) {
        return messageRepo.findById(threadId, id);
    }

    /**
     * additional messages的插入时间为run和此次run创建的assistant message之间
     * @param threadId
     * @param from - run的创建时间
     * @param to - 此次run创建的assistant message的创建时间
     * @return
     */
    public List<Message> getAdditionalMessages(String threadId, LocalDateTime from, LocalDateTime to) {
        List<MessageDb> messages = messageRepo.findByThreadIdWithIntervalIncludeHidden(threadId, from, to);
        return messages.stream().map(MessageUtils::convertToInfo).collect(Collectors.toList());
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
        List<MessageDb> messages = messageRepo.findByThreadIdWithLimit(threadId, runCreateAt);

        // 从后向前查找第一个命令类型的消息
        for (int i = messages.size() - 1; i >= 0; i--) {
            MessageDb message = messages.get(i);
            if ("command".equals(message.getMessageType())) {
                // 解析内容获取命令类型
                try {
                    List<Map<String, Object>> contentList = JacksonUtils.deserialize(message.getContent(), new TypeReference<>() {});
                    if (!contentList.isEmpty()) {
                        String commandType = (String) contentList.get(0).get("type");
                        // 如果是clear命令，只返回该命令之后的消息
                        if ("clear".equals(commandType)) {
                            return messages.subList(i + 1, messages.size()).stream().map(MessageUtils::convertToInfo).collect(Collectors.toList());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse command message content: {}", message.getContent(), e);
                }
            }
        }

        // 如果不需要过滤或没有找到clear命令，返回完整消息列表
        return messages.stream().map(MessageUtils::convertToInfo).collect(Collectors.toList());
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
        return messages.stream().map(MessageUtils::convertToInfo).collect(Collectors.toList());
    }

    /**
     * 更新Message
     */
    @Transactional
    public Message updateMessage(String threadId, String id, MessageRequest request) {
        MessageDb existing = messageRepo.findById(threadId, id);
        if(existing == null) {
            return null;
        }

        if(request.getContent() != null) {
            List<Object> formattedContent = MessageUtils.formatMessageContent(request.getContent());
            existing.setContent(JacksonUtils.serialize(formattedContent));
        }

        // 序列化metadata
        if(request.getMetadata() != null) {
            existing.setMetadata(JacksonUtils.serialize(request.getMetadata()));
        }

        messageRepo.update(existing);
        return convertToInfo(existing);
    }

    /**
     * 更新Message的内容 + reasoning
     */
    @Transactional
    public Message addContent(String threadId, String id, MessageContent content, String reasoning, Map<String, String> metaData) {
        MessageDb existing = messageRepo.findByIdForUpdate(threadId, id);
        if(existing == null) {
            return null;
        }

        List<MessageContent> contents = JacksonUtils.deserialize(existing.getContent(), new TypeReference<>() {});

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

        if(reasoning != null && !reasoning.isBlank()) {
            existing.setReasoningContent(reasoning);
        }

        // 修改内容可能会影响MessageType
        existing.setMessageType(MessageUtils.recognizeMessageType(existing.getContent()));

        if(metaData != null && !metaData.isEmpty()) {
            if(existing.getMetadata() != null) {
                existing.setMetadata(JacksonUtils.serialize(metaData));
            } else {
                Map<String, String> matas = JacksonUtils.deserialize(existing.getMetadata(), new TypeReference<>() {});
                matas.putAll(metaData);
                existing.setMetadata(JacksonUtils.serialize(matas));
            }
        }

        messageRepo.update(existing);
        return convertToInfo(existing);
    }

    @Transactional
    public Message updateStatus(String threadId, String id, String status, boolean hidden, IncompleteDetails details) {
        MessageDb existing = messageRepo.findByIdForUpdate(threadId, id);
        if(existing == null) {
            return null;
        }

        if(existing.getStatus().equals("incomplete")) {
            log.warn("Invalid status transition for message {}: {} -> {}",
                    id, existing.getStatus(), status);
            return convertToInfo(existing);
        }

        existing.setStatus(status);

        existing.setMessageStatus(hidden ? "hidden" : "original");

        if(details != null) {
            Map<String, String> matas;
            if(existing.getMetadata() != null) {
                matas = new HashMap<>();
            } else {
                matas = JacksonUtils.deserialize(existing.getMetadata(), new TypeReference<>() {});
            }
            matas.put(MetaConstants.INCOMPLETE_REASON, details.getReason());
            existing.setMetadata(JacksonUtils.serialize(matas));
        }

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

    private MessageDb convertToDb(Message message) {
        if(message == null) {
            return null;
        }

        MessageDb db = new MessageDb();
        BeanUtils.copyProperties(message, db);


        db.setObject("thread.message");

        db.setMetadata(JacksonUtils.serialize(message.getMetadata()));

        db.setContent(JacksonUtils.serialize(message.getContent()));

        // attachments字段反序列化
        db.setAttachments(JacksonUtils.serialize(message.getAttachments()));

        return db;
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

    public Message getTheLastMessage(String threadId) {
        List<MessageDb> messageDbs = messageRepo.findRecentByThreadId(threadId, 1);
        if(CollectionUtils.isEmpty(messageDbs)) {
            return null;
        }
        return convertToInfo(messageDbs.get(0));
    }

}
