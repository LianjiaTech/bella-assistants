package com.ke.assistant.controller;

import com.ke.assistant.db.repo.Page;
import com.ke.assistant.model.CommonPage;
import com.ke.assistant.model.DeleteResponse;
import com.ke.assistant.service.MessageService;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.common.exception.ResourceNotFoundException;
import com.theokanning.openai.assistants.message.Message;
import com.theokanning.openai.assistants.message.MessageRequest;
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
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {

        Page<Message> infoPage = messageService.getMessagesByThreadIdWithPage(threadId, page, pageSize);

        List<Message> infoList = infoPage.getList();

        String firstId = infoList.isEmpty() ? null : infoList.get(0).getId();
        String lastId = infoList.isEmpty() ? null : infoList.get(infoList.size() - 1).getId();
        boolean hasMore = (long) infoPage.getPage() * infoPage.getPageSize() < infoPage.getTotal();

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
