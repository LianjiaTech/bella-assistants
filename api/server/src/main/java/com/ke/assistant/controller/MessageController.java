package com.ke.assistant.controller;

import com.ke.assistant.common.CommonPage;
import com.ke.assistant.common.DeleteResponse;
import com.ke.assistant.db.repo.Page;
import com.ke.assistant.message.MessageInfo;
import com.ke.assistant.message.MessageOps;
import com.ke.assistant.service.MessageService;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
@Slf4j
public class MessageController {

    private final MessageService messageService;

    /**
     * 创建 Message
     */
    @PostMapping
    public MessageInfo createMessage(
            @PathVariable("thread_id") String threadId,
            @RequestBody MessageOps.CreateMessageOp request) {

        return messageService.createMessage(threadId, request);
    }

    /**
     * 获取 Message 详情
     */
    @GetMapping("/{message_id}")
    public MessageInfo getMessage(
            @PathVariable("thread_id") String threadId,
            @PathVariable("message_id") String messageId) {

        MessageInfo message = messageService.getMessageById(messageId);
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
    public CommonPage<MessageInfo> listMessages(
            @PathVariable("thread_id") String threadId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {

        Page<MessageInfo> infoPage = messageService.getMessagesByThreadIdWithPage(threadId, page, pageSize);

        List<MessageInfo> infoList = infoPage.getList();

        String firstId = infoList.isEmpty() ? null : infoList.get(0).getId();
        String lastId = infoList.isEmpty() ? null : infoList.get(infoList.size() - 1).getId();
        boolean hasMore = (long) infoPage.getPage() * infoPage.getPageSize() < infoPage.getTotal();

        return new CommonPage<>(infoList, firstId, lastId, hasMore);
    }

    /**
     * 更新 Message
     */
    @PostMapping("/{message_id}")
    public MessageInfo updateMessage(
            @PathVariable("thread_id") String threadId,
            @PathVariable("message_id") String messageId,
            @RequestBody MessageOps.UpdateMessageOp request) {

        // 验证消息是否存在且属于指定的thread
        MessageInfo existing = messageService.getMessageById(messageId);
        if(existing == null) {
            throw new ResourceNotFoundException("Message not found");
        }
        if(!threadId.equals(existing.getThreadId())) {
            throw new BizParamCheckException("Message does not belong to this thread");
        }

        return messageService.updateMessage(messageId, request);
    }

    /**
     * 删除 Message
     */
    @DeleteMapping("/{message_id}")
    public DeleteResponse deleteMessage(
            @PathVariable("thread_id") String threadId,
            @PathVariable("message_id") String messageId) {

        // 验证消息是否存在且属于指定的thread
        MessageInfo existing = messageService.getMessageById(messageId);
        if(existing == null) {
            throw new ResourceNotFoundException("Message not found");
        }
        if(!threadId.equals(existing.getThreadId())) {
            throw new BizParamCheckException("Message does not belong to this thread");
        }

        boolean deleted = messageService.deleteMessage(messageId);
        return new DeleteResponse(messageId, "thread.message", deleted);
    }

}
