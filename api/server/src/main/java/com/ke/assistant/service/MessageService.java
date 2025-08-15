package com.ke.assistant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.assistant.common.Attachment;
import com.ke.assistant.db.generated.tables.pojos.MessageDb;
import com.ke.assistant.db.repo.MessageRepo;
import com.ke.assistant.db.repo.Page;
import com.ke.assistant.message.MessageInfo;
import com.ke.assistant.message.MessageOps;
import com.ke.assistant.util.BeanUtils;
import com.ke.bella.openapi.common.exception.ResourceNotFoundException;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.completion.chat.MultiMediaContent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Message Service
 */
@Service
@Slf4j
public class MessageService {

    @Autowired
    private MessageRepo messageRepo;

    @Autowired
    private ThreadService threadService;
    
    @Autowired
    private ThreadLockService threadLockService;

    /**
     * 创建 Message（单条插入使用读锁，允许并发执行）
     */
    @Transactional
    public MessageInfo createMessage(String threadId, MessageOps.CreateMessageOp request) {
        // 验证Thread是否存在
        if (!threadService.existsById(threadId)) {
            throw new ResourceNotFoundException("Thread not found: " + threadId);
        }
        
        MessageDb message = new MessageDb();
        BeanUtils.copyProperties(request, message);
        message.setThreadId(threadId);

        // 格式化content内容
        if(request.getContent() != null) {
            List<Object> formattedContent = formatMessageContent(request.getContent());
            message.setContent(JacksonUtils.serialize(formattedContent));
        }

        // 序列化其他复杂字段
        if(request.getAttachments() != null) {
            message.setAttachments(JacksonUtils.serialize(request.getAttachments()));
        }
        if(request.getMetadata() != null) {
            message.setMetadata(JacksonUtils.serialize(request.getMetadata()));
        }

        // 设置默认值
        message.setObject("thread.message");

        // 只有实际的数据库插入操作需要加读锁
        MessageDb savedMessage = threadLockService.executeWithReadLock(threadId, () -> messageRepo.insert(message));
        
        return convertToInfo(savedMessage);
    }

    /**
     * 根据ID获取Message
     */
    public MessageInfo getMessageById(String id) {
        MessageDb messageDb = messageRepo.findById(id);
        return messageDb != null ? convertToInfo(messageDb) : null;
    }

    /**
     * 根据Thread ID查询Message列表
     */
    public List<MessageInfo> getMessagesByThreadId(String threadId) {
        List<MessageDb> messages = messageRepo.findByThreadId(threadId);
        return messages.stream().map(this::convertToInfo).collect(java.util.stream.Collectors.toList());
    }

    /**
     * 根据Run ID查询Message列表
     */
    public List<MessageInfo> getMessagesByRunId(String runId) {
        List<MessageDb> messages = messageRepo.findByRunId(runId);
        return messages.stream().map(this::convertToInfo).collect(java.util.stream.Collectors.toList());
    }

    /**
     * 根据Thread ID查询MessageDb列表
     */
    public List<MessageDb> getMessageDbsByThreadId(String threadId) {
        return messageRepo.findByThreadId(threadId);
    }

    /**
     * 分页查询Thread下的Message
     */
    public Page<MessageInfo> getMessagesByThreadIdWithPage(String threadId, int page, int pageSize) {
        // 验证Thread是否存在
        if (!threadService.existsById(threadId)) {
            throw new ResourceNotFoundException("Thread not found: " + threadId);
        }
        Page<MessageDb> dbPage = messageRepo.findByThreadIdWithPage(threadId, page, pageSize);
        List<MessageInfo> infoList = dbPage.getList().stream().map(this::convertToInfo).collect(java.util.stream.Collectors.toList());
        Page<MessageInfo> result = new Page<>();
        result.setPage(dbPage.getPage());
        result.setPageSize(dbPage.getPageSize());
        result.setTotal(dbPage.getTotal());
        result.setList(infoList);
        return result;
    }

    /**
     * 更新Message
     */
    @Transactional
    public MessageInfo updateMessage(String id, MessageOps.UpdateMessageOp request) {
        MessageDb existing = messageRepo.findById(id);
        if(existing == null) {
            throw new IllegalArgumentException("Message not found: " + id);
        }

        MessageDb updateData = new MessageDb();
        BeanUtils.copyProperties(request, updateData);

        // 格式化content内容
        if(request.getContent() != null) {
            List<Object> formattedContent = formatMessageContent(request.getContent());
            updateData.setContent(JacksonUtils.serialize(formattedContent));
        }

        // 序列化metadata
        if(request.getMetadata() != null) {
            updateData.setMetadata(JacksonUtils.serialize(request.getMetadata()));
        }

        BeanUtils.copyNonNullProperties(updateData, existing);

        messageRepo.update(existing);
        return convertToInfo(messageRepo.findById(id));
    }

    /**
     * 删除Message
     */
    @Transactional
    public boolean deleteMessage(String id) {
        return messageRepo.deleteById(id);
    }

    /**
     * 删除Thread下的所有Message
     */
    @Transactional
    public int deleteMessagesByThreadId(String threadId) {
        return messageRepo.deleteByThreadId(threadId);
    }

    /**
     * 检查Message是否存在
     */
    public boolean existsById(String id) {
        return messageRepo.existsById(id);
    }

    /**
     * 将MessageDb转换为MessageInfo
     */
    private MessageInfo convertToInfo(MessageDb messageDb) {
        if(messageDb == null) {
            return null;
        }

        MessageInfo info = new MessageInfo();
        BeanUtils.copyProperties(messageDb, info);

        // 转换metadata从JSON字符串到Map
        if(StringUtils.isNotBlank(messageDb.getMetadata())) {
            info.setMetadata(JacksonUtils.toMap(messageDb.getMetadata()));
        }

        // content字段处理 - 数据库存储的是Content对象数组的JSON
        if(StringUtils.isNotBlank(messageDb.getContent())) {
            try {
                // 数据库中存储的格式：[{"type": "text", "text": {"value": "内容", "annotations": []}}]
                info.setContent(JacksonUtils.deserialize(messageDb.getContent(), new TypeReference<Object>() {
                }));
            } catch (Exception e) {
                log.warn("Failed to deserialize message content: {}", messageDb.getContent(), e);
                // 如果反序列化失败，尝试作为字符串处理
                info.setContent(messageDb.getContent());
            }
        }

        // attachments字段反序列化
        if(StringUtils.isNotBlank(messageDb.getAttachments())) {
            try {
                info.setAttachments(JacksonUtils.deserialize(messageDb.getAttachments(), new TypeReference<List<Attachment>>() {
                }));
            } catch (Exception e) {
                log.warn("Failed to deserialize message attachments: {}", messageDb.getAttachments(), e);
            }
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


    /**
     * 格式化消息内容，将内容转换为用于存储的Content格式
     */
    private List<Object> formatMessageContent(Object content) {
        List<Object> result = new ArrayList<>();

        if(content == null) {
            return result;
        }

        if(content instanceof String) {
            // 字符串内容转换为MessageContentText格式
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");

            Map<String, Object> text = new HashMap<>();
            text.put("value", content);
            text.put("annotations", new ArrayList<>());
            textContent.put("text", text);

            result.add(textContent);
        } else if(content instanceof List) {
            // 列表内容，遍历每个元素
            List<?> contentList = (List<?>) content;
            for (Object item : contentList) {
                if(item instanceof MultiMediaContent) {
                    MultiMediaContent mmContent = (MultiMediaContent) item;

                    if("text".equals(mmContent.getType())) {
                        Map<String, Object> contentItem = new HashMap<>();
                        contentItem.put("type", "text");
                        Map<String, Object> text = new HashMap<>();
                        if(mmContent.getText() != null) {
                            text.put("value", mmContent.getText());
                        }
                        text.put("annotations", new ArrayList<>());
                        contentItem.put("text", text);
                        result.add(contentItem);
                    } else {
                        // 其他类型（image_file, image_url等）直接使用原类型
                        result.add(item);
                    }
                } else {
                    throw new IllegalArgumentException("Unsupported content type.");
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported content type.");
        }

        return result;
    }
}
