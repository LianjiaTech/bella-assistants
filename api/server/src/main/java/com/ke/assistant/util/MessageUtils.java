package com.ke.assistant.util;

import com.ke.assistant.db.generated.tables.pojos.MessageDb;
import com.ke.assistant.util.BeanUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Message 工具类
 */
public class MessageUtils {

    /**
     * 获取消息的源ID
     * 如果消息有copy_message_id元数据，返回copy_message_id；否则返回消息本身的ID
     */
    public static String getSourceId(MessageDb message) {
        if (message == null) {
            return null;
        }
        
        // 优先检查是否有copy_message_id
        if (StringUtils.isNotBlank(message.getMetadata())) {
            try {
                Map<String, Object> metadata = JacksonUtils.toMap(message.getMetadata());
                String copyMessageId = (String) metadata.get("copy_message_id");
                if (copyMessageId != null) {
                    return copyMessageId;
                }
            } catch (Exception e) {
                // 如果解析失败，fallback到使用消息ID
            }
        }
        
        // 返回消息本身的ID
        return message.getId();
    }

    /**
     * 获取消息列表中所有的源ID集合
     * 用于重复检测
     */
    public static Set<String> getExistingSourceIds(List<MessageDb> messages) {
        Set<String> existingIds = new HashSet<>();
        
        for (MessageDb message : messages) {
            String sourceId = getSourceId(message);
            if (sourceId != null) {
                existingIds.add(sourceId);
            }
        }
        
        return existingIds;
    }

    /**
     * 检查消息是否已存在（基于源ID）
     */
    public static boolean isMessageExists(MessageDb message, Set<String> existingSourceIds) {
        if (message == null || existingSourceIds == null) {
            return false;
        }
        
        String sourceId = getSourceId(message);
        return sourceId != null && existingSourceIds.contains(sourceId);
    }

    /**
     * 复制消息到指定Thread
     * 处理ID重新生成、线程ID设置、copy_message_id元数据标记
     */
    @SuppressWarnings("unchecked")
    public static MessageDb copyMessageToThread(MessageDb sourceMessage, String targetThreadId) {
        if (sourceMessage == null || targetThreadId == null) {
            return null;
        }

        MessageDb newMessage = new MessageDb();
        BeanUtils.copyProperties(sourceMessage, newMessage);
        newMessage.setId(null); // 重新生成ID
        newMessage.setThreadId(targetThreadId);

        // 在metadata中标记原消息ID
        String metadata = sourceMessage.getMetadata();
        Map<String, Object> metadataMap = new HashMap<>();
        if (StringUtils.isNotBlank(metadata)) {
            try {
                metadataMap = JacksonUtils.toMap(metadata);
            } catch (Exception e) {
                // 如果解析失败，使用空Map
                metadataMap = new HashMap<>();
            }
        }
        metadataMap.put("copy_message_id", sourceMessage.getId());
        newMessage.setMetadata(JacksonUtils.serialize(metadataMap));

        return newMessage;
    }
}
