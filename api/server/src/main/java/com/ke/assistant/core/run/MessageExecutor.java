package com.ke.assistant.core.run;

import com.google.common.collect.Lists;
import com.ke.assistant.core.TaskExecutor;
import com.ke.assistant.util.MessageUtils;
import com.theokanning.openai.OpenAiError;
import com.theokanning.openai.Usage;
import com.theokanning.openai.assistants.StreamEvent;
import com.theokanning.openai.assistants.message.Message;
import com.theokanning.openai.assistants.message.MessageContent;
import com.theokanning.openai.assistants.message.content.Delta;
import com.theokanning.openai.assistants.message.content.DeltaContent;
import com.theokanning.openai.assistants.message.content.ImageFile;
import com.theokanning.openai.assistants.message.content.MessageDelta;
import com.theokanning.openai.assistants.message.content.Text;
import com.theokanning.openai.assistants.run.Run;
import com.theokanning.openai.assistants.run.ToolCall;
import com.theokanning.openai.assistants.run_step.RunStep;
import com.theokanning.openai.assistants.run_step.RunStepDelta;
import com.theokanning.openai.assistants.run_step.StepDetails;
import com.theokanning.openai.common.LastError;
import com.theokanning.openai.completion.chat.AssistantMessage;
import com.theokanning.openai.completion.chat.ChatCompletionChunk;
import com.theokanning.openai.completion.chat.ChatToolCall;
import com.theokanning.openai.completion.chat.ImageUrl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class MessageExecutor implements Runnable {
    private final ExecutionContext context;
    private final RunStateManager runStateManager;
    private final SseEmitter sseEmitter;
    private StringBuilder content;
    private StringBuilder reasoning;
    // 助手消息中的内容序号
    private Integer index;
    private Usage usage;

    public MessageExecutor(ExecutionContext context, RunStateManager runStateManager, SseEmitter sseEmitter) {
        this.context = context;
        this.runStateManager = runStateManager;
        this.sseEmitter = sseEmitter;
        this.content = new StringBuilder();
        this.reasoning = new StringBuilder();
        this.index = 0;
    }

    public static void start(ExecutionContext context, RunStateManager runStateManager, SseEmitter sseEmitter) {
        TaskExecutor.addExecutor(new MessageExecutor(context, runStateManager, sseEmitter));
    }

    @Override
    public void run() {
        while (!context.isEnd()) {
            try {
                Object msg = context.consume();
                // 线程未结束时才执行
                if(!context.isEnd()) {
                    process(msg);
                }
            } catch (Exception e) {
                context.setError("server_error", e.getMessage());
                log.warn(e.getMessage(), e);
                if(sseEmitter != null) {
                    sseEmitter.completeWithError(e);
                }
            }
        }
    }

    private void process(Object msg) throws IOException {
        if(msg instanceof String){
            // 结束消息
            if(msg.equals("[DONE]")) {
                try {
                    finish();
                } finally {
                    context.finishSend();
                }
                return;
            }
            // llm处理结束
            if(msg.equals("[LLM_DONE]")) {
                context.addUsage(usage);
                // 没有工具调用，代表助手消息创建完毕
                if(!context.hasInProgressToolCalls()) {
                    MessageContent messageContent = new MessageContent();
                    messageContent.setType("text");
                    messageContent.setText(new Text(content.toString(), new ArrayList<>()));
                    runStateManager.finishMessageCreation(context, messageContent, reasoning.toString(), usage);
                    ++index;
                } else {
                    runStateManager.startToolCalls(context, reasoning.toString(), usage);
                }
                usage = null;
                content = new StringBuilder();
                reasoning = new StringBuilder();
                return;
            }
            // 工具处理结束时，存在content输出的工具会发送此消息，需要将输出加入到助手消息中
            if(msg.equals("[TOOL_DONE]")) {
                MessageContent messageContent = new MessageContent();
                messageContent.setType("text");
                messageContent.setText(new Text(content.toString(), new ArrayList<>()));
                runStateManager.addContent(context, messageContent, null);
                content = new StringBuilder();
                // 新增内容，序号 +1
                ++index;
                context.finishToolCallOutput();
                return;
            }
            // 工具作为Final的输出
            sendContent((String) msg);
            content.append(msg);
            return;
        }
        // 异常消息
        if(msg instanceof LastError) {
            try {
                OpenAiError error = new OpenAiError();
                OpenAiError.OpenAiErrorDetails details = new OpenAiError.OpenAiErrorDetails();
                details.setMessage(((LastError) msg).getMessage());
                details.setType(((LastError) msg).getCode());
                details.setCode(((LastError) msg).getCode());
                error.setError(details);
                send(StreamEvent.ERROR, error);
                finish();
            } finally {
                context.finishSend();
            }
            return;
        }
        // 各种状态变更事件的消息
        if(msg instanceof Thread) {
            send(StreamEvent.THREAD_CREATED, msg);
            return;
        }
        if(msg instanceof Run) {
            RunStatus status = RunStatus.fromValue(((Run) msg).getStatus());
            if(RunStatus.QUEUED == status) {
                send(StreamEvent.THREAD_RUN_CREATED, msg);
            }
            send(status.getRunStreamEvent(), msg);
            return;
        }
        if(msg instanceof ResumeMessage) {
            send(StreamEvent.THREAD_RUN_STEP_COMPLETED, ((ResumeMessage) msg).getRunStep());
            send(StreamEvent.THREAD_RUN_QUEUED, ((ResumeMessage) msg).getRun());
            return;
        }
        if(msg instanceof RunStep) {
            RunStatus status = RunStatus.fromValue(((RunStep) msg).getStatus());
            send(status.getRunStepStreamEvent(), msg);
            return;
        }
        if(msg instanceof Message) {
            if(((Message) msg).getStatus().equals("incomplete")) {
                send(StreamEvent.THREAD_MESSAGE_INCOMPLETE, msg);
            } else if(((Message) msg).getStatus().equals("completed")){
                send(StreamEvent.THREAD_MESSAGE_COMPLETED, msg);
            } else {
                send(StreamEvent.THREAD_MESSAGE_IN_PROGRESS, msg);
            }
            return;
        }
        // llm调用的消息
        if(msg instanceof ChatCompletionChunk) {
            ChatCompletionChunk chunk = new ChatCompletionChunk();
            if(CollectionUtils.isNotEmpty(chunk.getChoices())) {
                AssistantMessage assistantMessage = chunk.getChoices().get(0).getMessage();
                if(assistantMessage != null) {
                    if(assistantMessage.getReasoningContent() != null) {
                        sendReasoning(assistantMessage.getReasoningContent());
                        reasoning.append(assistantMessage.getReasoningContent());
                    }
                    if(assistantMessage.getContent() != null) {
                        sendContent(assistantMessage.getContent());
                        content.append(assistantMessage.getContent());
                    }
                    if(assistantMessage.getToolCalls() != null) {
                        for(ChatToolCall chatToolCall : assistantMessage.getToolCalls()) {
                            sendToolCall(chatToolCall);
                            context.addToolCallTask(chatToolCall);
                        }
                    }
                }
            }
            if(chunk.getUsage() != null) {
                usage = chunk.getUsage();
            }
            return;
        }
        //以下是Tool为isFinal会发送的多模态消息
        if(msg instanceof ImageFile) {
            ImageFile imageFile = (ImageFile) msg;
            sendImageFile(imageFile);
            MessageContent messageContent = new MessageContent();
            messageContent.setType("image_file");
            messageContent.setImageFile(imageFile);
            runStateManager.addContent(context, messageContent, null);
            // 新增内容，序号 +1
            index++;
            context.finishToolCallOutput();
            return;
        }
        if(msg instanceof ImageUrl) {
            ImageUrl imageUrl = (ImageUrl) msg;
            sendImageUrl(imageUrl);
            MessageContent messageContent = new MessageContent();
            messageContent.setType("image_url");
            messageContent.setImageUrl(imageUrl);
            runStateManager.addContent(context, messageContent, null);
            // 新增内容，序号 +1
            index++;
            context.finishToolCallOutput();
        }
    }

    private void finish() throws IOException {
        if(sseEmitter == null) {
            return;
        }
        send(StreamEvent.DONE, "[DONE]");
        sseEmitter.complete();
    }

    private void sendContent(String content) throws IOException {
        if(sseEmitter == null) {
            return;
        }
        MessageDelta messageDelta = new MessageDelta();
        messageDelta.setId(context.getAssistantMessageId());
        messageDelta.setObject("thread.message.delta");
        Delta delta = new Delta();
        delta.setRole("assistant");
        List<DeltaContent> contents = new ArrayList<>();
        DeltaContent deltaContent = new DeltaContent();
        deltaContent.setIndex(index);
        deltaContent.setType("text");
        deltaContent.setText(new Text(content, new ArrayList<>()));
        contents.add(deltaContent);
        delta.setContent(contents);
        messageDelta.setDelta(delta);
        send(StreamEvent.THREAD_MESSAGE_DELTA, messageDelta);
    }

    private void sendReasoning(String reasoning) throws IOException {
        if(sseEmitter == null) {
            return;
        }
        MessageDelta messageDelta = new MessageDelta();
        messageDelta.setId(context.getAssistantMessageId());
        messageDelta.setObject("thread.message.delta");
        Delta delta = new Delta();
        delta.setRole("assistant");
        delta.setReasoningContent(reasoning);
        messageDelta.setDelta(delta);
        send(StreamEvent.THREAD_MESSAGE_DELTA, messageDelta);
    }

    private void sendToolCall(ChatToolCall chatToolCall) throws IOException {
        if(sseEmitter == null) {
            return;
        }
        StepDetails stepDetails = new StepDetails();
        ToolCall call = MessageUtils.convertToolCall(chatToolCall);
        stepDetails.setToolCalls(Lists.newArrayList(call));
        RunStepDelta delta = new RunStepDelta();
        com.theokanning.openai.assistants.run_step.Delta rDelta = new com.theokanning.openai.assistants.run_step.Delta();
        rDelta.setStepDetails(stepDetails);
        delta.setDelta(rDelta);
        delta.setObject("run. step. delta");
        send(StreamEvent.THREAD_RUN_STEP_DELTA, delta);
    }

    private void sendImageFile(ImageFile imageFile) throws IOException {
        MessageDelta messageDelta = new MessageDelta();
        messageDelta.setId(context.getAssistantMessageId());
        messageDelta.setObject("thread.message.delta");
        Delta delta = new Delta();
        delta.setRole("assistant");
        List<DeltaContent> contents = new ArrayList<>();
        DeltaContent deltaContent = new DeltaContent();
        deltaContent.setIndex(index);
        deltaContent.setType("image_file");
        deltaContent.setImageFile(imageFile);
        contents.add(deltaContent);
        delta.setContent(contents);
        messageDelta.setDelta(delta);
        send(StreamEvent.THREAD_MESSAGE_DELTA, messageDelta);
    }

    private void sendImageUrl(ImageUrl imageUrl) throws IOException {
        MessageDelta messageDelta = new MessageDelta();
        messageDelta.setId(context.getAssistantMessageId());
        messageDelta.setObject("thread.message.delta");
        Delta delta = new Delta();
        delta.setRole("assistant");
        List<DeltaContent> contents = new ArrayList<>();
        DeltaContent deltaContent = new DeltaContent();
        deltaContent.setIndex(index);
        deltaContent.setType("image_url");
        deltaContent.setImageUrl(imageUrl);
        contents.add(deltaContent);
        delta.setContent(contents);
        messageDelta.setDelta(delta);
        send(StreamEvent.THREAD_MESSAGE_DELTA, messageDelta);
    }

    private void send(StreamEvent type, Object data) throws IOException {
        if(sseEmitter == null) {
            return;
        }
        SseEmitter.SseEventBuilder builder = SseEmitter.event().name(type.eventName).data(data);
        sseEmitter.send(builder);
    }

}
