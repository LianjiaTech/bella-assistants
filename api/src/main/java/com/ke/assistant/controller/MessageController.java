package com.ke.assistant.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ke.assistant.model.CommonPage;
import com.ke.assistant.model.DeleteResponse;
import com.ke.assistant.service.MessageService;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.common.exception.ResourceNotFoundException;
import com.theokanning.openai.assistants.message.Message;
import com.theokanning.openai.assistants.message.MessageRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * Message Controller
 */
@RestController
@RequestMapping("/v1/threads/{thread_id}/messages")
@Slf4j
public class MessageController {

    @Autowired
    private MessageService messageService;

    /**
     * 创建 Message
     */
    @PostMapping
    public Message createMessage(
            @PathVariable("thread_id") String threadId,
            @RequestBody MessageRequest request) {

        return messageService.createMessage(threadId, request);
    }

    /**
     * 获取 Message 详情
     */
    @GetMapping("/{message_id}")
    public Message getMessage(
            @PathVariable("thread_id") String threadId,
            @PathVariable("message_id") String messageId) {

        Message message = messageService.getMessageById(threadId, messageId);
        if(message == null) {
            throw new ResourceNotFoundException("Message not found");
        }

        // 验证消息是否属于指定的thread
        if(!threadId.equals(message.getThreadId())) {
            throw new BizParamCheckException("Message does not belong to this thread");
        }

        return message;
    }

    /**
     * 获取 Thread 的消息列表
     */
    @GetMapping
    public CommonPage<Message> listMessages(
            @PathVariable("thread_id") String threadId,
            @RequestParam(value = "after", required = false) String after,
            @RequestParam(value = "before", required = false) String before,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "order", defaultValue = "desc") String order) {

        List<Message> infoList = messageService.getMessagesByCursor(threadId, after, before, limit + 1, order);

        boolean hasMore = infoList.size() > limit;
        if (hasMore) {
            infoList.remove(infoList.size() - 1);
        }

        String firstId = infoList.isEmpty() ? null : infoList.get(0).getId();
        String lastId = infoList.isEmpty() ? null : infoList.get(infoList.size() - 1).getId();

        return new CommonPage<>(infoList, firstId, lastId, hasMore);
    }

    /**
     * 更新 Message
     */
    @PostMapping("/{message_id}")
    public Message updateMessage(
            @PathVariable("thread_id") String threadId,
            @PathVariable("message_id") String messageId,
            @RequestBody MessageRequest request) {

        // 验证消息是否存在且属于指定的thread
        Message existing = messageService.getMessageById(threadId, messageId);
        if(existing == null) {
            throw new ResourceNotFoundException("Message not found");
        }

        if(!threadId.equals(existing.getThreadId())) {
            throw new BizParamCheckException("Message does not belong to this thread");
        }

        return messageService.updateMessage(threadId, messageId, request);
    }

    /**
     * 删除 Message
     */
    @DeleteMapping("/{message_id}")
    public DeleteResponse deleteMessage(
            @PathVariable("thread_id") String threadId,
            @PathVariable("message_id") String messageId) {

        // 验证消息是否存在且属于指定的thread
        Message existing = messageService.getMessageById(threadId, messageId);
        if(existing == null) {
            throw new ResourceNotFoundException("Message not found");
        }

        if(!threadId.equals(existing.getThreadId())) {
            throw new BizParamCheckException("Message does not belong to this thread");
        }


        boolean deleted = messageService.deleteMessage(threadId, messageId);
        return new DeleteResponse(messageId, "thread.message", deleted);
    }

}
