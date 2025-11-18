package com.ke.assistant.core.run;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.Lists;
import com.ke.assistant.core.TaskExecutor;
import com.ke.assistant.core.tools.ToolExecutor;
import com.ke.assistant.core.tools.ToolStreamEvent;
import com.ke.assistant.util.AnnotationUtils;
import com.ke.assistant.util.MetaConstants;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.Usage;
import com.theokanning.openai.assistants.message.MessageContent;
import com.theokanning.openai.assistants.message.content.Text;
import com.theokanning.openai.assistants.run.Run;
import com.theokanning.openai.common.LastError;
import com.theokanning.openai.completion.chat.AssistantMessage;
import com.theokanning.openai.completion.chat.ChatCompletionChunk;
import com.theokanning.openai.completion.chat.ChatToolCall;
import com.theokanning.openai.completion.chat.ImageUrl;
import com.theokanning.openai.response.ItemStatus;
import com.theokanning.openai.response.MessageRole;
import com.theokanning.openai.response.Response;
import com.theokanning.openai.response.ResponseItem;
import com.theokanning.openai.response.ResponseStatus;
import com.theokanning.openai.response.content.OutputContentValue;
import com.theokanning.openai.response.content.OutputMessage;
import com.theokanning.openai.response.content.OutputText;
import com.theokanning.openai.response.content.Reasoning;
import com.theokanning.openai.response.stream.BaseStreamEvent;
import com.theokanning.openai.response.stream.ContentPartAddedEvent;
import com.theokanning.openai.response.stream.ContentPartDoneEvent;
import com.theokanning.openai.response.stream.ErrorEvent;
import com.theokanning.openai.response.stream.FunctionCallArgumentsDeltaEvent;
import com.theokanning.openai.response.stream.FunctionCallArgumentsDoneEvent;
import com.theokanning.openai.response.stream.OutputItemAddedEvent;
import com.theokanning.openai.response.stream.OutputItemDoneEvent;
import com.theokanning.openai.response.stream.OutputTextDeltaEvent;
import com.theokanning.openai.response.stream.OutputTextDoneEvent;
import com.theokanning.openai.response.stream.ReasoningSummaryPartAddedEvent;
import com.theokanning.openai.response.stream.ReasoningSummaryTextDeltaEvent;
import com.theokanning.openai.response.stream.ReasoningSummaryTextDoneEvent;
import com.theokanning.openai.response.stream.ResponseCompletedEvent;
import com.theokanning.openai.response.stream.ResponseCreatedEvent;
import com.theokanning.openai.response.stream.ResponseFailedEvent;
import com.theokanning.openai.response.stream.ResponseInProgressEvent;
import com.theokanning.openai.response.stream.ResponseIncompleteEvent;
import com.theokanning.openai.response.tool.FunctionToolCall;
import com.theokanning.openai.response.tool.ToolCall;

import lombok.extern.slf4j.Slf4j;

/**
 * Response API Message Executor Converts chat completion streaming to Response API streaming events
 */
@Slf4j
public class ResponseMessageExecutor implements Runnable {

    private final ExecutionContext context;
    private final RunStateManager runStateManager;
    private final ToolExecutor toolExecutor;
    private final StringBuilder currentText = new StringBuilder();
    private final StringBuilder currentArguments = new StringBuilder();
    private final StringBuilder currentReasoning = new StringBuilder();
    private final StringBuilder currentReasoningSignature = new StringBuilder();
    private final StringBuilder currentRedactedReasoningContent = new StringBuilder();
    // Response building
    private final List<ResponseItem> outputItems = new ArrayList<>();
    private final Response currentResponse;
    private SseEmitter sseEmitter;
    // State tracking
    private int sequenceNumber = 0;
    private int outputIndex = 0;
    private int contentIndex = 0;
    // Current item tracking
    private String currentItemId;
    private int currentToolCallIndex = -1;
    private Usage usage;
    // Event boundaries
    private boolean reasoningStarted = false;
    private boolean messageStarted = false;
    private boolean functionCallStarted = false;
    private String currentToolCalStepId;

    public ResponseMessageExecutor(ExecutionContext context, RunStateManager runStateManager, ToolExecutor toolExecutor, SseEmitter sseEmitter) {
        this.context = context;
        this.runStateManager = runStateManager;
        this.toolExecutor = toolExecutor;
        this.sseEmitter = sseEmitter;
        this.currentResponse = context.getResponse();
    }

    public static void start(ExecutionContext context, RunStateManager runStateManager, ToolExecutor toolExecutor, SseEmitter sseEmitter) {
        TaskExecutor.addExecutor(new ResponseMessageExecutor(context, runStateManager, toolExecutor, sseEmitter));
    }

    @Override
    public void run() {

        while (true) {
            try {
                Object msg = context.consume();
                if(context.isEnd() && "[END]".equals(msg)) {
                    break;
                }
                process(msg);
            } catch (Exception e) {
                context.setError("server_error", e.getMessage());
                log.warn(e.getMessage(), e);
            }
        }

        try {
            finish();
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        } finally {
            context.finishSend();
        }
    }

    private void process(Object msg) {
        if(msg instanceof Run run) {
            handleResponseMessage(run);
        }

        if(msg instanceof String s) {
            handleStringMessage(s);
            return;
        }

        if(msg instanceof LastError lastError) {
            handleError(lastError);
            return;
        }

        if(msg instanceof ChatCompletionChunk chunk) {
            handleChatCompletionChunk(chunk);
            return;
        }

        if(msg instanceof ToolStreamEvent toolStreamEvent) {
            handleToolStreamEvent(toolStreamEvent);
            return;
        }

        if(msg instanceof ImageUrl imageUrl) {
            MessageContent messageContent = new MessageContent();
            messageContent.setType("image_url");
            messageContent.setImageUrl(imageUrl);
            runStateManager.addContent(context, messageContent, null, null);
        }
    }

    private void handleResponseMessage(Run run) {
        RunStatus status = RunStatus.fromValue((run).getStatus());
        if(status.isStopExecution()) {
            finishPreviousItem();
            // Update final response
            currentResponse.setOutput(outputItems);
            currentResponse.setUsage(Response.Usage.fromChatUsage(context.getUsage()));

            if(status == RunStatus.FAILED) {
                currentResponse.setStatus(ResponseStatus.FAILED);
                Response.ErrorDetails errorDetails = new Response.ErrorDetails();
                if(run.getLastError() != null) {
                    errorDetails.setMessage(run.getLastError().getMessage());
                    errorDetails.setCode(run.getLastError().getCode());
                } else {
                    errorDetails.setCode("server_error");
                    errorDetails.setMessage("unexpected error");
                }
                currentResponse.setError(errorDetails);
                sendEvent(ResponseFailedEvent.builder()
                        .sequenceNumber(sequenceNumber++)
                        .response(currentResponse)
                        .build());
            } else if(status == RunStatus.CANCELLED || status == RunStatus.EXPIRED) {
                currentResponse.setStatus(ResponseStatus.INCOMPLETE);
                currentResponse.setIncompleteDetails(new Response.IncompleteDetails(
                        run.getIncompleteDetails() == null ? "unknown reason" : run.getIncompleteDetails().getReason()));
                sendEvent(ResponseIncompleteEvent.builder()
                        .sequenceNumber(sequenceNumber++)
                        .response(currentResponse)
                        .build());
            } else {
                currentResponse.setStatus(ResponseStatus.COMPLETED);
                sendEvent(ResponseCompletedEvent.builder()
                        .sequenceNumber(sequenceNumber++)
                        .response(currentResponse)
                        .build());
            }
        } else if(status == RunStatus.IN_PROGRESS) {
            // Send initial response.created event
            sendEvent(ResponseCreatedEvent.builder()
                    .sequenceNumber(sequenceNumber++)
                    .response(currentResponse)
                    .build());

            // Send response.in_progress event
            currentResponse.setStatus(ResponseStatus.IN_PROGRESS);
            sendEvent(ResponseInProgressEvent.builder()
                    .sequenceNumber(sequenceNumber++)
                    .response(currentResponse)
                    .build());
        }
    }

    private void handleStringMessage(String msg) {
        if("[DONE]".equals(msg)) {
            try {
                finish();
            } finally {
                context.finishSend();
            }
            return;
        }

        if("[LLM_DONE]".equals(msg)) {
            if(context.isError()) {
                return;
            }
            finishPreviousItem();
            Map<String, String> meatData = new HashMap<>();
            String reasoningSignature = null;
            String redactedReasoningContent = null;
            String reasoning = null;
            StringBuilder content = new StringBuilder();

            for (ResponseItem item : outputItems) {
                if(item instanceof Reasoning reasoningItem) {
                    reasoning = reasoningItem.getSummary().get(0).getText();
                    reasoningSignature = reasoningItem.getSummary().get(0).getReasoningSignature();
                    redactedReasoningContent = reasoningItem.getSummary().get(0).getRedactedReasoningContent();
                }
                if(item instanceof OutputMessage message) {
                    OutputText outputText = (OutputText) message.getContent().getArrayValue().get(0);
                    if(!content.isEmpty()) {
                        content.append("\n");
                    }
                    content.append(outputText.getText());
                }
            }

            if(StringUtils.isNotBlank(reasoningSignature)) {
                meatData.put(MetaConstants.REASONING_SIG, reasoningSignature);
            }
            if(StringUtils.isNotBlank(redactedReasoningContent)) {
                meatData.put(MetaConstants.REDACTED_REASONING, redactedReasoningContent);
            }
            context.addUsage(usage);
            // 没有工具调用，代表助手消息创建完毕
            if(!context.hasInProgressToolCalls()) {
                MessageContent messageContent = new MessageContent();
                messageContent.setType("text");
                messageContent.setText(new Text(content.toString(), context.getAnnotations()));
                runStateManager.finishMessageCreation(context, messageContent, reasoning, usage, meatData);
            } else {
                if(!content.isEmpty()) {
                    meatData.put(MetaConstants.TEXT, content.toString());
                }
                if(reasoning != null) {
                    meatData.put(MetaConstants.REASONING, reasoning);
                }
                runStateManager.startToolCalls(context, usage, meatData);
                context.getCurrentMetaData().putAll(meatData);
            }
        }
    }

    private void handleError(LastError error) {
        ErrorEvent errorEvent = ErrorEvent.builder()
                .sequenceNumber(sequenceNumber++)
                .code(error.getCode())
                .message(error.getMessage())
                .build();
        sendEvent(errorEvent);
        finish();
    }

    private void handleChatCompletionChunk(ChatCompletionChunk chunk) {
        if(CollectionUtils.isNotEmpty(chunk.getChoices())) {
            AssistantMessage assistantMessage = chunk.getChoices().get(0).getMessage();
            if(assistantMessage != null) {

                if(assistantMessage.getReasoningContentSignature() != null) {
                    currentReasoningSignature.append(assistantMessage.getReasoningContentSignature());
                }
                if(assistantMessage.getRedactedReasoningContent() != null) {
                    currentRedactedReasoningContent.append(assistantMessage.getRedactedReasoningContent());
                }

                // Handle reasoning content
                if(assistantMessage.getReasoningContent() != null && !assistantMessage.getReasoningContent().isEmpty()) {
                    ensureReasoningItem();
                    sendReasoningDelta(assistantMessage.getReasoningContent());
                    currentReasoning.append(assistantMessage.getReasoningContent());
                }

                // Handle content
                if(assistantMessage.getContent() != null && !assistantMessage.getContent().isEmpty()) {
                    ensureMessageItem();
                    sendTextDelta(assistantMessage.getContent());
                    currentText.append(assistantMessage.getContent());
                }

                // Handle tool calls
                if(assistantMessage.getToolCalls() != null) {
                    if(context.generateCurrentToolCallStepId() || currentToolCalStepId == null) {
                        // 缓存currentToolCalStepId
                        currentToolCalStepId = context.getCurrentToolCallStepId();
                    }
                    for (ChatToolCall toolCall : assistantMessage.getToolCalls()) {
                        context.addToolCallTask(toolCall);
                        handleToolCall(toolCall);
                    }
                }
            }
        }

        if(chunk.getUsage() != null) {
            usage = chunk.getUsage();
        }

        if(chunk.getError() != null) {
            context.setError(chunk.getError().getCode(), chunk.getError().getMessage());
        }
    }

    private void handleToolCall(ChatToolCall toolCall) {

        if(toolCall.getFunction() == null) {
            return;
        }

        if(StringUtils.isNotBlank(toolCall.getId()) && StringUtils.isNotBlank(toolCall.getFunction().getName())) {
            if(!toolExecutor.canExecute(toolCall.getFunction().getName())) {
                ensureFunctionCallItem(toolCall);
            }
        }

        if(toolCall.getIndex() != currentToolCallIndex) {
            return;
        }

        // Send function call arguments delta
        if(toolCall.getFunction().getArguments() != null) {
            JsonNode argNodes = toolCall.getFunction().getArguments();
            String args;
            if(argNodes instanceof TextNode) {
                args = argNodes.asText();
            } else {
                args = JacksonUtils.serialize(argNodes);
            }
            if(args != null && !args.isEmpty()) {
                sendFunctionArgumentsDelta(args);
                currentArguments.append(args);
            }
        }
    }

    private void handleToolStreamEvent(ToolStreamEvent toolStreamEvent) {
        ToolCall toolCall = toolStreamEvent.getResult();
        if(toolStreamEvent.getExecutionStage() == ToolStreamEvent.ExecutionStage.prepare) {
            currentItemId = generateItemId(context.getCurrentOutputToolCallId());
            outputIndex++;
            if(toolCall != null) {
                toolCall.setId(currentItemId);
            }
            OutputItemAddedEvent outputItemAddedEvent = new OutputItemAddedEvent();
            outputItemAddedEvent.setItemId(currentItemId);
            outputItemAddedEvent.setOutputIndex(outputIndex - 1);
            outputItemAddedEvent.setSequenceNumber(sequenceNumber++);
            outputItemAddedEvent.setItem(toolCall);
            sendEvent(outputItemAddedEvent);
        }
        BaseStreamEvent event = toolStreamEvent.getEvent();
        if(event != null) {
            event.setSequenceNumber(sequenceNumber++);
            event.setItemId(currentItemId);
            event.setOutputIndex(outputIndex - 1);
            sendEvent(event);
        }
        if(toolStreamEvent.getExecutionStage() == ToolStreamEvent.ExecutionStage.completed) {
            if(toolCall != null) {
                toolCall.setId(currentItemId);
            }
            OutputItemDoneEvent outputItemDoneEvent = new OutputItemDoneEvent();
            outputItemDoneEvent.setItemId(currentItemId);
            outputItemDoneEvent.setOutputIndex(outputIndex - 1);
            outputItemDoneEvent.setSequenceNumber(sequenceNumber++);
            outputItemDoneEvent.setItem(toolCall);
            sendEvent(outputItemDoneEvent);
            outputItems.add(toolCall);
            context.finishToolCallOutput();
        }
    }

    private void ensureReasoningItem() {
        if(!reasoningStarted) {
            finishPreviousItem();

            currentItemId = generateItemId();
            reasoningStarted = true;
            messageStarted = false;
            functionCallStarted = false;

            Reasoning reasoning = new Reasoning();
            reasoning.setId(currentItemId);
            reasoning.setSummary(new ArrayList<>());

            outputItems.add(reasoning);

            sendEvent(OutputItemAddedEvent.builder()
                    .sequenceNumber(sequenceNumber++)
                    .outputIndex(outputIndex++)
                    .item(reasoning)
                    .build());

            // Add reasoning summary part
            Reasoning.SummaryText summaryText = new Reasoning.SummaryText();
            summaryText.setText("");

            sendEvent(ReasoningSummaryPartAddedEvent.builder()
                    .sequenceNumber(sequenceNumber++)
                    .itemId(currentItemId)
                    .outputIndex(outputIndex - 1)
                    .summaryIndex(contentIndex++)
                    .part(summaryText)
                    .build());
        }
    }

    private void ensureMessageItem() {
        if(!messageStarted) {
            finishPreviousItem();

            currentItemId = generateItemId();
            messageStarted = true;
            reasoningStarted = false;
            functionCallStarted = false;

            OutputMessage message = new OutputMessage();
            message.setId(currentItemId);
            message.setRole(MessageRole.ASSISTANT);
            message.setStatus(ItemStatus.IN_PROGRESS);
            message.setContent(new OutputContentValue(new ArrayList<>()));

            outputItems.add(message);

            sendEvent(OutputItemAddedEvent.builder()
                    .sequenceNumber(sequenceNumber++)
                    .outputIndex(outputIndex++)
                    .item(message)
                    .build());

            // Add content part
            OutputText outputText = new OutputText();
            outputText.setText("");
            outputText.setAnnotations(new ArrayList<>());

            sendEvent(ContentPartAddedEvent.builder()
                    .sequenceNumber(sequenceNumber++)
                    .itemId(currentItemId)
                    .outputIndex(outputIndex - 1)
                    .contentIndex(contentIndex++)
                    .part(outputText)
                    .build());
        }
    }

    private void ensureFunctionCallItem(ChatToolCall toolCall) {
        if(!functionCallStarted || currentToolCallIndex != toolCall.getIndex()) {
            finishPreviousItem();

            currentItemId = generateItemId(toolCall.getId());
            currentToolCallIndex = toolCall.getIndex();
            functionCallStarted = true;
            reasoningStarted = false;
            messageStarted = false;

            FunctionToolCall functionCall = new FunctionToolCall();
            functionCall.setId(currentItemId);
            functionCall.setStatus(ItemStatus.IN_PROGRESS);
            functionCall.setName(toolCall.getFunction().getName());
            functionCall.setCallId(toolCall.getId());

            outputItems.add(functionCall);

            sendEvent(OutputItemAddedEvent.builder()
                    .sequenceNumber(sequenceNumber++)
                    .outputIndex(outputIndex++)
                    .item(functionCall)
                    .build());
        }
    }

    private void sendReasoningDelta(String delta) {
        sendEvent(ReasoningSummaryTextDeltaEvent.builder()
                .sequenceNumber(sequenceNumber++)
                .itemId(currentItemId)
                .outputIndex(outputIndex - 1)
                .summaryIndex(contentIndex - 1)
                .delta(delta)
                .build());
    }

    private void sendTextDelta(String delta) {
        sendEvent(OutputTextDeltaEvent.builder()
                .sequenceNumber(sequenceNumber++)
                .itemId(currentItemId)
                .outputIndex(outputIndex - 1)
                .contentIndex(contentIndex - 1)
                .delta(delta)
                .build());
    }

    private void sendFunctionArgumentsDelta(String delta) {
        sendEvent(FunctionCallArgumentsDeltaEvent.builder()
                .sequenceNumber(sequenceNumber++)
                .itemId(currentItemId)
                .outputIndex(outputIndex - 1)
                .delta(delta)
                .build());
    }

    private void finishPreviousItem() {
        if(reasoningStarted) {
            finishReasoningItem();
        } else if(messageStarted) {
            finishMessageItem();
        } else if(functionCallStarted) {
            finishFunctionCallItem();
        }
        contentIndex = 0;
        currentToolCallIndex = -1;
    }

    private void finishReasoningItem() {
        if(reasoningStarted) {
            // Send reasoning summary done
            sendEvent(ReasoningSummaryTextDoneEvent.builder()
                    .sequenceNumber(sequenceNumber++)
                    .itemId(currentItemId)
                    .outputIndex(outputIndex - 1)
                    .summaryIndex(contentIndex - 1)
                    .text(currentReasoning.toString())
                    .build());

            // Send output item done
            Reasoning reasoning = (Reasoning) findOutputItem(currentItemId);
            Reasoning.SummaryText summaryText = new Reasoning.SummaryText();
            summaryText.setText(currentReasoning.toString());
            summaryText.setReasoningSignature(currentReasoningSignature.toString());
            summaryText.setRedactedReasoningContent(currentRedactedReasoningContent.toString());
            reasoning.setSummary(Lists.newArrayList(summaryText));
            sendEvent(OutputItemDoneEvent.builder()
                    .sequenceNumber(sequenceNumber++)
                    .outputIndex(outputIndex - 1)
                    .item(reasoning)
                    .build());

            reasoningStarted = false;
            currentReasoning.setLength(0);
            currentReasoningSignature.setLength(0);
            currentRedactedReasoningContent.setLength(0);
        }
    }

    private void finishMessageItem() {
        if(messageStarted) {
            // Send output text done
            sendEvent(OutputTextDoneEvent.builder()
                    .sequenceNumber(sequenceNumber++)
                    .itemId(currentItemId)
                    .outputIndex(outputIndex - 1)
                    .contentIndex(contentIndex - 1)
                    .text(currentText.toString())
                    .build());

            // Send content part done
            OutputText outputText = new OutputText();
            outputText.setText(currentText.toString());
            outputText.setAnnotations(AnnotationUtils.convertToResponseAnnotations(context.getAnnotations()));

            sendEvent(ContentPartDoneEvent.builder()
                    .sequenceNumber(sequenceNumber++)
                    .itemId(currentItemId)
                    .outputIndex(outputIndex - 1)
                    .contentIndex(contentIndex - 1)
                    .part(outputText)
                    .build());

            // Update message status and send output item done
            OutputMessage message = (OutputMessage) findOutputItem(currentItemId);
            if(message != null) {
                message.setStatus(ItemStatus.COMPLETED);
                message.setContent(new OutputContentValue(Lists.newArrayList(outputText)));
            }

            sendEvent(OutputItemDoneEvent.builder()
                    .sequenceNumber(sequenceNumber++)
                    .outputIndex(outputIndex - 1)
                    .item(message)
                    .build());

            messageStarted = false;
            currentText.setLength(0);
        }
    }

    private void finishFunctionCallItem() {
        if(functionCallStarted) {
            // Send function call arguments done
            sendEvent(FunctionCallArgumentsDoneEvent.builder()
                    .sequenceNumber(sequenceNumber++)
                    .itemId(currentItemId)
                    .outputIndex(outputIndex - 1)
                    .arguments(currentArguments.toString())
                    .build());

            // Update function call status and send output item done
            FunctionToolCall functionCall = (FunctionToolCall) findOutputItem(currentItemId);
            if(functionCall != null) {
                functionCall.setStatus(ItemStatus.COMPLETED);
                functionCall.setArguments(currentArguments.toString());
            }

            sendEvent(OutputItemDoneEvent.builder()
                    .sequenceNumber(sequenceNumber++)
                    .outputIndex(outputIndex - 1)
                    .item(functionCall)
                    .build());

            functionCallStarted = false;
            currentArguments.setLength(0);
        }
    }

    private ResponseItem findOutputItem(String itemId) {
        return outputItems.stream()
                .filter(item -> itemId.equals(item.getId()))
                .findFirst()
                .orElse(null);
    }

    private void finish() {
        if(sseEmitter != null) {
            sseEmitter.complete();
            sseEmitter = null;
        }
        context.complete(currentResponse);
    }

    private String generateItemId() {
        return context.getRunId() + "_" + outputIndex;
    }

    private String generateItemId(String toolCallId) {
        return context.getThreadId() + "_" + currentToolCalStepId + "_" + toolCallId;
    }

    private void sendEvent(BaseStreamEvent event) {
        if(sseEmitter == null) {
            return;
        }
        try {
            SseEmitter.SseEventBuilder builder = SseEmitter.event()
                    .name(event.getType())
                    .data(event);
            sseEmitter.send(builder);
        } catch (IOException e) {
            log.warn("failed to send:{}", event);
            log.warn("Failed to send SSE event", e);
        }
    }
}
