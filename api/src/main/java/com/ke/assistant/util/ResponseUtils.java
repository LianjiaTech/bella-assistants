package com.ke.assistant.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.Lists;
import com.ke.assistant.core.run.RunStatus;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.assistants.assistant.Tool;
import com.theokanning.openai.assistants.message.Message;
import com.theokanning.openai.assistants.message.MessageContent;
import com.theokanning.openai.assistants.message.content.Approval;
import com.theokanning.openai.assistants.message.content.AudioData;
import com.theokanning.openai.assistants.message.content.ImageFile;
import com.theokanning.openai.assistants.message.content.Text;
import com.theokanning.openai.assistants.run.AllowedTools;
import com.theokanning.openai.assistants.run.Run;
import com.theokanning.openai.assistants.run.RunCreateRequest;
import com.theokanning.openai.assistants.run.ToolChoice;
import com.theokanning.openai.assistants.run.TruncationStrategy;
import com.theokanning.openai.assistants.run_step.RunStep;
import com.theokanning.openai.assistants.run_step.StepDetails;
import com.theokanning.openai.assistants.thread.Attachment;
import com.theokanning.openai.completion.chat.ChatFunctionCall;
import com.theokanning.openai.completion.chat.ChatToolCall;
import com.theokanning.openai.completion.chat.ImageUrl;
import com.theokanning.openai.completion.chat.ToolMessage;
import com.theokanning.openai.response.ConversationItem;
import com.theokanning.openai.response.ConversationValue;
import com.theokanning.openai.response.CreateResponseRequest;
import com.theokanning.openai.response.InputValue;
import com.theokanning.openai.response.InstructionsValue;
import com.theokanning.openai.response.ItemReference;
import com.theokanning.openai.response.ItemStatus;
import com.theokanning.openai.response.MessageRole;
import com.theokanning.openai.response.Response;
import com.theokanning.openai.response.ResponseStatus;
import com.theokanning.openai.response.ToolChoiceValue;
import com.theokanning.openai.response.content.Annotation;
import com.theokanning.openai.response.content.InputAudio;
import com.theokanning.openai.response.content.InputContent;
import com.theokanning.openai.response.content.InputContentValue;
import com.theokanning.openai.response.content.InputFile;
import com.theokanning.openai.response.content.InputImage;
import com.theokanning.openai.response.content.InputMessage;
import com.theokanning.openai.response.content.InputText;
import com.theokanning.openai.response.content.OutputContent;
import com.theokanning.openai.response.content.OutputContentValue;
import com.theokanning.openai.response.content.OutputMessage;
import com.theokanning.openai.response.content.OutputText;
import com.theokanning.openai.response.content.Reasoning;
import com.theokanning.openai.response.content.Refusal;
import com.theokanning.openai.response.tool.ComputerToolCall;
import com.theokanning.openai.response.tool.CustomToolCall;
import com.theokanning.openai.response.tool.FileSearchToolCall;
import com.theokanning.openai.response.tool.FunctionToolCall;
import com.theokanning.openai.response.tool.ImageGenerationToolCall;
import com.theokanning.openai.response.tool.LocalShellToolCall;
import com.theokanning.openai.response.tool.MCPApprovalRequest;
import com.theokanning.openai.response.tool.MCPApprovalResponse;
import com.theokanning.openai.response.tool.MCPListTools;
import com.theokanning.openai.response.tool.MCPToolCall;
import com.theokanning.openai.response.tool.ToolCall;
import com.theokanning.openai.response.tool.WebSearchToolCall;
import com.theokanning.openai.response.tool.output.ComputerToolCallOutput;
import com.theokanning.openai.response.tool.output.CustomToolCallOutput;
import com.theokanning.openai.response.tool.output.FunctionToolCallOutput;
import com.theokanning.openai.response.tool.output.LocalShellCallOutput;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ResponseUtils {

    @FunctionalInterface
    public interface RunStepFetcher {
        RunStep fetch(String threadId, String stepId);
    }


    @FunctionalInterface
    public interface ImageUrlProcessor {
        String processImageUrl(String imageUrl);
    }

    @FunctionalInterface
    public interface AudioUploader {
        String upload(String base64Data, String format);
    }

    public static RunCreateRequest convertToRunRequest(CreateResponseRequest request, String systemPrompt, List<Tool> tools,
            String responseId, String threadId, String previousThreadId, String previousRunId) {
        // Create run request
        RunCreateRequest runCreateRequest = new RunCreateRequest();
        Map<String, String> metadata = new HashMap<>();
        runCreateRequest.setAssistantId(null);
        runCreateRequest.setModel(request.getModel());
        runCreateRequest.setInstructions(request.getInstructions());
        runCreateRequest.setAdditionalInstructions(systemPrompt);
        runCreateRequest.setTools(tools);
        runCreateRequest.setToolChoice(convertToolChoice(request.getToolChoice()));
        runCreateRequest.setUser(runCreateRequest.getUser());

        runCreateRequest.setTemperature(request.getTemperature());
        runCreateRequest.setTopP(request.getTopP());
        runCreateRequest.setMaxCompletionTokens(request.getMaxOutputTokens());

        if(request.getTruncation() == null || request.getTruncation().equals("disable")) {
            TruncationStrategy strategy = new TruncationStrategy();
            strategy.setType("disable");
            runCreateRequest.setTruncationStrategy(strategy);
        } else {
            runCreateRequest.setTruncationStrategy(request.getTruncationStrategy());
        }
        runCreateRequest.setSaveMessage(request.getStore());

        if(request.getReasoning() != null) {
            runCreateRequest.setReasoningEffort(request.getReasoning().getEffort());
        }

        // Add Response API specific metadata
        if(request.getMetadata() != null) {
            metadata.putAll(request.getMetadata());
        }
        metadata.put(MetaConstants.RESPONSE_ID, responseId);
        // 默认为true
        metadata.put(MetaConstants.PARALLEL_TOOL_CALLS, String.valueOf(Boolean.FALSE != request.getParallelToolCalls()));
        // 默认为true
        metadata.put(MetaConstants.STORE, String.valueOf(Boolean.FALSE != request.getStore()));
        // 默认为false
        metadata.put(MetaConstants.BACKGROUND, String.valueOf(Boolean.TRUE == request.getBackground()));
        if(previousRunId != null) {
            metadata.put(MetaConstants.PREVIOUS_RES_ID, request.getPreviousResponseId());
            metadata.put(MetaConstants.PREVIOUS_RUN_ID, previousRunId);
        }
        if(previousThreadId != null) {
            metadata.put(MetaConstants.PREVIOUS_THREAD_ID, threadId);
        }
        runCreateRequest.setMetadata(metadata);
        return runCreateRequest;
    }

    public static ToolChoice convertToolChoice(ToolChoiceValue toolChoiceValue) {
        if(toolChoiceValue == null) {
            return ToolChoice.AUTO;
        }
        if(toolChoiceValue.isString()) {
            switch (toolChoiceValue.getStringValue()) {
            case "required":
                return ToolChoice.REQUIRED;
            case "NONE":
                return ToolChoice.NONE;
            default:
                return ToolChoice.AUTO;
            }
        }
        if(toolChoiceValue.isAllowedChoice()) {
            ToolChoiceValue.AllowedToolsChoice allowedToolsChoice = (ToolChoiceValue.AllowedToolsChoice) toolChoiceValue.getObjectValue();
            AllowedTools allowedTools = new AllowedTools();
            allowedTools.setTools(allowedToolsChoice.getTools().stream().map(
                    toolDefinition -> {
                        com.theokanning.openai.assistants.run.Tool tool = new com.theokanning.openai.assistants.run.Tool();
                        tool.setType("function");
                        tool.setFunction(new com.theokanning.openai.assistants.run.Function(toolDefinition.getName()));
                        return tool;
                    }
            ).collect(Collectors.toList()));
            allowedTools.setMode(allowedToolsChoice.getMode());
            return new ToolChoice(allowedTools);
        }
        if(toolChoiceValue.isSpecificChoice()) {
            ToolChoiceValue.SpecificToolChoice specificToolChoice = (ToolChoiceValue.SpecificToolChoice) toolChoiceValue.getObjectValue();
            com.theokanning.openai.assistants.run.Function function = new com.theokanning.openai.assistants.run.Function(specificToolChoice.getName());
            return new ToolChoice(function);
        }
        return ToolChoice.AUTO;
    }

    public static Response buildResponseFromRun(Run run, String responseId) {
        Response.ResponseBuilder builder = Response.builder();

        Map<String, String> metadata = run.getMetadata();


        builder.id(responseId)
                .object("response")
                .createdAt(run.getCreatedAt().longValue())
                .model(run.getModel())
                .temperature(run.getTemperature())
                .topP(run.getTopP())
                .maxOutputTokens(run.getMaxCompletionTokens())
                .instructions(new InstructionsValue(run.getInstructions()))
                // todo: tool choice converter
                //.toolChoice()
                .parallelToolCalls(Boolean.parseBoolean(metadata.get(MetaConstants.PARALLEL_TOOL_CALLS)))
                .background(Boolean.parseBoolean(metadata.get(MetaConstants.BACKGROUND)))
                .previousResponseId(metadata.get(MetaConstants.PREVIOUS_RES_ID))
                .truncation(run.getTruncationStrategy() != null ? run.getTruncationStrategy().getType() : "auto");

        if(Boolean.parseBoolean(run.getMetadata().get(MetaConstants.STORE))) {
            // Add conversation reference
            ConversationValue conversationValue = ConversationValue.of(run.getThreadId());
            builder.conversation(conversationValue);
        }

        // Add usage if available
        builder.usage(Response.Usage.fromChatUsage(run.getUsage()));

        if(run.getIncompleteDetails() != null) {
            builder.incompleteDetails(new Response.IncompleteDetails(run.getIncompleteDetails().getReason()));
        }

        RunStatus status = RunStatus.fromValue((run).getStatus());
        if(status.isStopExecution()) {
            if(status == RunStatus.FAILED) {
                builder.status(ResponseStatus.FAILED);
            } else if(status == RunStatus.CANCELLED || status == RunStatus.EXPIRED) {
                builder.status(ResponseStatus.INCOMPLETE);
            }
            builder.status(ResponseStatus.COMPLETED);
        } else {
            builder.status(ResponseStatus.IN_PROGRESS);
        }

        builder.tools(ToolUtils.convertToToolDefinition(run.getTools()));

        return builder.build();
    }


    public static List<Message> checkAndConvertInputToMessages(CreateResponseRequest request,
                                                               RunStepFetcher fetcher,
                                                               ImageUrlProcessor imageProcessor,
                                                               AudioUploader audioUploader) {
        List<Message> messages = new ArrayList<>();
        if(request.getInput() != null) {
            if(request.getInput().getInputType() == InputValue.InputType.STRING) {
                Message message = new Message();
                message.setRole("user");
                message.setContent(Lists.newArrayList(textContent(request.getInput().getStringValue())));
                messages.add(message);
            } else if(request.getInput().getInputType() == InputValue.InputType.OBJECT_LIST) {
                messages = convertConversationItemsToMessages(request.getInput().getObjectListValue(), fetcher, imageProcessor, audioUploader);
            }
        }
        Set<String> approves = new HashSet<>();
        for(int i = 1; i < messages.size(); i++) {
            Message cur = messages.get(i);
            Message prev = messages.get(i - 1);
            MessageUtils.checkPre(prev, cur, approves);
        }
        if(!approves.isEmpty()) {
            throw new BizParamCheckException("approves required with id: " +  JacksonUtils.serialize(approves));
        }
        return messages;
    }

    private static List<Message> convertConversationItemsToMessages(List<ConversationItem> conversationItems,
                                                                    RunStepFetcher fetcher,
                                                                    ImageUrlProcessor imageProcessor,
                                                                    AudioUploader audioUploader) {
        Map<String, RunStep> runStepMap = new HashMap<>();
        List<Message> messages = new ArrayList<>();
        messages.add(message());
        for(ConversationItem conversationItem : conversationItems) {
            Message last = messages.get(messages.size() - 1);
            if(conversationItem instanceof InputMessage inputMessage) {
                processInputMessage(inputMessage, last, messages, imageProcessor, audioUploader);
            } else if(conversationItem instanceof OutputMessage outputMessage) {
                processOutputMessage(outputMessage, last, messages);
            }  else if(conversationItem instanceof Reasoning reasoning) {
                processReasoning(reasoning, last, messages);
            } else if(conversationItem instanceof ToolCall toolCall) {
                processToolCall(toolCall, last, messages, runStepMap, fetcher);
            } else if(conversationItem instanceof FunctionToolCallOutput output) {
                ToolMessage toolMessage = toolResult(output.getCallId(), output.getOutput());
                setToolResult(toolMessage, last, messages, output.getType());
            }  else if(conversationItem instanceof CustomToolCallOutput output) {
                ToolMessage toolMessage = toolResult(output.getCallId(), output.getOutput());
                setToolResult(toolMessage, last, messages, output.getType());
            }  else if(conversationItem instanceof MCPApprovalResponse approvalResponse) {
                setApprove(approvalResponse, last, messages, runStepMap, fetcher);
            } else if(conversationItem instanceof ComputerToolCallOutput) {
                //todo: 处理computer use
                throw new BizParamCheckException("can not supported ComputerUseToolCallOutput");
            } else if(conversationItem instanceof ItemReference) {
                //todo: ItemReference
                throw new BizParamCheckException("can not support ItemReference");
            } else {
                throw new BizParamCheckException("unexpected input type");
            }
        }
        return messages;
    }

    private static void processInputMessage(InputMessage inputMessage,
                                            Message last,
                                            List<Message> messages,
                                            ImageUrlProcessor imageProcessor,
                                            AudioUploader audioUploader) {
        String role = inputMessage.getRole().getValue();
        Message message = setRoleOrNewMessage(last, role, messages);
        if(inputMessage.getContent().isString()) {
            message.getContent().add(textContent(inputMessage.getContent().getStringValue()));
        } else {
            for(InputContent inputContent : inputMessage.getContent().getArrayValue()) {
                if(inputContent instanceof InputText inputText) {
                    message.getContent().add(textContent(inputText.getText()));
                } else if(inputContent instanceof InputImage inputImage) {
                    MessageContent messageContent = new MessageContent();
                    if(inputImage.getImageUrl() != null) {
                        // Process image URL - convert base64 to S3 URL if needed
                        String processedUrl = imageProcessor != null ? imageProcessor.processImageUrl(inputImage.getImageUrl()) : inputImage.getImageUrl();
                        messageContent.setType("image_url");
                        messageContent.setImageUrl(new ImageUrl(processedUrl, inputImage.getDetail()));
                        message.getContent().add(messageContent);
                    } else {
                        messageContent.setType("image_file");
                        messageContent.setImageFile(new ImageFile(inputImage.getFileId(), inputImage.getDetail()));
                        message.getContent().add(messageContent);
                    }
                } else if(inputContent instanceof InputFile inputFile) {
                    message.getAttachments().add(new Attachment(inputFile.getFileId(), Lists.newArrayList(new Tool.Retrieval(true, true), new Tool.ReadFiles(true, true))));
                } else if(inputContent instanceof InputAudio inputAudio) {
                    InputAudio.AudioData inAudio = inputAudio.getInputAudio();
                    if (inAudio == null || inAudio.getData() == null || inAudio.getFormat() == null) {
                        throw new BizParamCheckException("invalid audio input: missing data or format");
                    }

                    String fileId = audioUploader.upload(inAudio.getData(), inAudio.getFormat());

                    // Store audio as MessageContent type "audio" with audio_data payload
                    MessageContent audioContent = new MessageContent();
                    audioContent.setType("audio");
                    AudioData audioDataOut = new AudioData();
                    audioDataOut.setFileId(fileId);
                    audioDataOut.setFormat(inAudio.getFormat());
                    audioContent.setAudioData(audioDataOut);
                    message.getContent().add(audioContent);
                    message.getAttachments().add(new Attachment(fileId, Lists.newArrayList(new Tool.AudioTranscription())));
                }
            }
        }
    }

    private static void processOutputMessage(OutputMessage outputMessage, Message last, List<Message> messages) {
        String role = outputMessage.getRole().getValue();
        Message message = setRoleOrNewMessage(last, role, messages);
        if(outputMessage.getContent().isString()) {
            message.getContent().add(textContent(outputMessage.getContent().getStringValue()));
        } else {
            for(OutputContent outputContent : outputMessage.getContent().getArrayValue()) {
                if(outputContent instanceof OutputText outputText) {
                    message.getContent().add(textContent(outputText.getText(), outputText.getAnnotations()));
                } else if(outputContent instanceof Refusal refusal) {
                    message.getContent().add(textContent(refusal.getRefusal()));
                }
            }
        }
    }

    private static void processReasoning(Reasoning reasoning, Message last, List<Message> messages) {
        Message message = setRoleOrNewMessage(last, "assistant", messages);
        message.setRole("assistant");
        if(reasoning.getSummary() != null) {
            message.setReasoningContent(reasoning.getSummary().stream().map(Reasoning.SummaryText::getText).reduce(String::concat).orElse(""));
        }
        if(reasoning.getContent() != null) {
            message.setReasoningContent((Optional.ofNullable(message.getReasoningContent()).orElse("")
                    .concat(reasoning.getContent().stream().map(Reasoning.ReasoningText::getText).reduce(String::concat).orElse(""))));
        }
    }

    private static void processToolCall(ToolCall toolCallItem, Message last, List<Message> messages, Map<String, RunStep> runStepMap, RunStepFetcher fetcher) {
        if(toolCallItem instanceof FunctionToolCall call) {
            ChatToolCall toolCall = chatToolCall(call.getCallId(), JacksonUtils.deserialize(call.getArguments()));
            setToolCall(toolCall, last, messages, toolCallItem.getType());
        }  else if(toolCallItem instanceof LocalShellToolCall localShellToolCall) {
            // tool call
            ChatToolCall toolCall = chatToolCall(localShellToolCall.getCallId(),
                    JacksonUtils.deserialize(JacksonUtils.serialize(localShellToolCall.getAction())));
            last = setToolCall(toolCall, last, messages, toolCallItem.getType());
            // tool result
            String output = localShellToolCall.getStatus() == ItemStatus.INCOMPLETE ? "Failed to run the local shell." : "Finish to run the local shell.";
            ToolMessage toolResult = toolResult(localShellToolCall.getCallId(), output);
            setToolResult(toolResult, last, messages, toolCallItem.getType());
        } else if(toolCallItem instanceof CustomToolCall customToolCall) {
            Map<String, String> inputMap = new HashMap<>();
            inputMap.put("input_data", customToolCall.getInput());
            ChatToolCall toolCall = chatToolCall(customToolCall.getCallId(), JacksonUtils.deserialize(JacksonUtils.serialize(inputMap)));
            setToolCall(toolCall, last, messages, toolCallItem.getType());
        }  else if(toolCallItem instanceof ComputerToolCall) {
            //todo: 处理computer use
            throw new BizParamCheckException("can not supported ComputerUseToolCall");
        } else if(toolCallItem instanceof MCPApprovalRequest) {
            Pair<ChatToolCall, ToolMessage> pair = getToolInfo(toolCallItem, runStepMap, fetcher);
            ChatToolCall toolCall = pair.getLeft();
            Assert.notNull(toolCall, "invalid mcp_approval_request id");
            Message tooCallMessage = setToolCall(toolCall, last, messages, toolCallItem.getType());
            tooCallMessage.getMetadata().put("item_id", toolCallItem.getId());
            tooCallMessage.getMetadata().put("approve_request", "true");
        } else {
            Pair<ChatToolCall, ToolMessage> pair = getToolInfo(toolCallItem, runStepMap, fetcher);
            ChatToolCall toolCall = pair.getLeft();
            ToolMessage toolResult = pair.getRight();
            Assert.notNull(toolCall, "invalid tool call id");
            Assert.notNull(toolResult, "invalid tool call id");
            // tool call
            Message tooCallMessage = setToolCall(toolCall, last, messages, toolCallItem.getType());
            tooCallMessage.getMetadata().put("item_id", toolCallItem.getId());
            // tool result
            Message toolResultMessage = setToolResult(toolResult, tooCallMessage, messages, toolCallItem.getType());
            toolResultMessage.getMetadata().put("item_id", toolCallItem.getId());
        }
    }

    private static ChatToolCall chatToolCall(String toolCallId, JsonNode arguments) {
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId(toolCallId);
        toolCall.setType("function");
        ChatFunctionCall functionCall = new ChatFunctionCall();
        functionCall.setName("local_shell");
        functionCall.setArguments(arguments);
        toolCall.setFunction(functionCall);
        return toolCall;
    }

    private static Message setToolCall(ChatToolCall toolCall, Message last, List<Message> messages, String type) {
        Message tooCallMessage = setRoleOrNewMessage(last, "assistant", messages);
        MessageContent toolCallContent = new MessageContent();
        toolCallContent.setType("tool_call");
        toolCallContent.setToolCall(toolCall);
        tooCallMessage.getContent().add(toolCallContent);
        Map<String, String> meta = new HashMap<>();
        meta.put("item_type", type);
        tooCallMessage.setMetadata(meta);
        return tooCallMessage;
    }

    private static ToolMessage toolResult(String toolCallId, String output) {
        ToolMessage toolResult = new ToolMessage();
        toolResult.setToolCallId(toolCallId);
        toolResult.setContent(output);
        return toolResult;
    }

    private static Message setToolResult(ToolMessage toolResult, Message last, List<Message> messages, String type) {
        Message toolResultMessage = setRoleOrNewMessage(last, "tool", messages);
        MessageContent toolResultContent = new MessageContent();
        toolResultContent.setType("tool_result");
        toolResultContent.setToolResult(toolResult);
        toolResultMessage.getContent().add(toolResultContent);
        Map<String, String> meta = new HashMap<>();
        meta.put("item_type", type);
        toolResultMessage.setMetadata(meta);
        return toolResultMessage;
    }

    private static void setApprove(MCPApprovalResponse approvalResponse, Message last, List<Message> messages, Map<String, RunStep> runStepCache, RunStepFetcher fetcher) {
        Assert.hasText(approvalResponse.getApprovalRequestId(), "approval request id must not be empty");
        Assert.notNull(approvalResponse.getApprove(), "approval request approve must not be null");
        ChatToolCall call = getToolInfo(approvalResponse.getApprovalRequestId(), "mcp_approval_request", runStepCache, fetcher).getLeft();
        Message approveMessage = setRoleOrNewMessage(last, "approval", messages);
        MessageContent approveContent = new MessageContent();
        approveContent.setType("approval");
        Approval approval = new Approval();
        approval.setApprovalRequestId(approvalResponse.getApprovalRequestId());
        approval.setApprove(approvalResponse.getApprove());
        approval.setIndex(call.getIndex());
        approval.setArguments(JacksonUtils.serialize(call.getFunction().getArguments()));
        String name = call.getFunction().getName();
        String[] strs = name.split("_", 2);
        if (strs.length == 2) {
            approval.setServerLabel(strs[0]);
            if(strs[1].length() > 9) {
                approval.setName(strs[1].substring(0, strs[1].length() - 9));
            } else {
                throw new IllegalStateException("invalid mcp approval tool name");
            }
        } else {
            throw new IllegalStateException("invalid mcp approval tool name");
        }
        approveContent.setApproval(approval);
        Map<String, String> meta = new HashMap<>();
        approveMessage.setMetadata(meta);
        approveMessage.getContent().add(approveContent);
    }

    private static Pair<ChatToolCall, ToolMessage> getToolInfo(ToolCall toolCall, Map<String, RunStep> runStepCache, RunStepFetcher fetcher) {
        return getToolInfo(toolCall.getId(), toolCall.getType(), runStepCache, fetcher);
    }

    private static Pair<ChatToolCall, ToolMessage> getToolInfo(String id, String type, Map<String, RunStep> runStepCache, RunStepFetcher fetcher) {
        Triple<String, String, String> triple = extractIds(id, type);
        RunStep runStep = runStepCache.computeIfAbsent(triple.getMiddle(), key -> fetcher.fetch(triple.getLeft(), triple.getMiddle()));
        Assert.notNull(runStep, "invalid id with tool call: " + type);
        return extractToolInfo(runStep, triple.getRight());
    }

    public static Triple<String, String, String> extractIds(String itemId, String type) {
        Assert.hasText(itemId, "Id is required with tool call: " + type);
        String[] ids = itemId.split("_");
        if(ids.length <= 4) {
            throw new BizParamCheckException("invalid id with tool call: " + type);
        }
        String threadId = ids[0].concat("_").concat(ids[1]);
        String stepId = ids[2].concat("_").concat(ids[3]);
        String toolCallId = ids[4];
        for(int i = 5; i < ids.length; i++) {
            toolCallId = toolCallId.concat("_").concat(ids[i]);
        }
        return Triple.of(threadId, stepId, toolCallId);
    }

    private static Pair<ChatToolCall, ToolMessage> extractToolInfo(RunStep runStep, String toolCallId) {
        StepDetails stepDetails = runStep.getStepDetails();
        Map<String, com.theokanning.openai.assistants.run.ToolCall> stepToolCalls = stepDetails.getToolCalls().stream().collect(Collectors.toMap(com.theokanning.openai.assistants.run.ToolCall::getId, Function.identity()));
        com.theokanning.openai.assistants.run.ToolCall toolCall = stepToolCalls.get(toolCallId);
        Assert.notNull(toolCall, "invalid tool call id");
        ChatToolCall chatToolCall = MessageUtils.convertToChatToolCall(toolCall);
        ToolMessage toolMessage = MessageUtils.convertToToolMessage(toolCall, runStep.getLastError());
        return Pair.of(chatToolCall, toolMessage);
    }

    private static Message setRoleOrNewMessage(Message last, String role, List<Message> messages) {
        if(last.getRole() == null) {
            last.setRole(role);
            return last;
        }
        if(!role.equals(last.getRole())) {
            Message message = message();
            message.setRole(role);
            messages.add(message);
            return message;
        }
        return last;
    }

    private static Message message() {
        Message message = new Message();
        message.setContent(new ArrayList<>());
        message.setAttachments(new ArrayList<>());
        message.setMetadata(new HashMap<>());
        return message;
    }

    private static MessageContent textContent(String data) {
        MessageContent content = new MessageContent();
        content.setType("text");
        Text text = new Text();
        text.setValue(data);
        text.setAnnotations(new ArrayList<>());
        content.setText(text);
        return content;
    }

    private static MessageContent textContent(String data, List<Annotation> annotations) {
        MessageContent content = textContent(data);
        content.getText().setAnnotations(AnnotationUtils.convertFromResponseAnnotations(annotations));
        return content;
    }

    public static List<ConversationItem> convertMessagesToConversationItems(List<Message> messages) {
        List<ConversationItem> conversationItems = new ArrayList<>();

        for (Message message : messages) {
            if (message == null || message.getContent() == null || message.getContent().isEmpty()) {
                continue;
            }

            String role = message.getRole();
            List<MessageContent> contents = message.getContent();

            // Process reasoning content first if present
            processReasoningContent(message, conversationItems);

            // Group regular text/image contents into a single message
            List<InputContent> inputContents = new ArrayList<>();
            List<OutputContent> outputContents = new ArrayList<>();

            for (MessageContent content : contents) {
                if (content == null) continue;

                String type = content.getType();

                switch (type) {
                    case "text":
                        processTextContent(content, role, inputContents, outputContents);
                        break;

                    case "image_url":
                        processImageUrlContent(content, role, inputContents);
                        break;

                    case "image_file":
                        processImageFileContent(content, role, inputContents);
                        break;
                }
            }

            // Add accumulated text/image contents as messages
            addAccumulatedContents(role, inputContents, outputContents, conversationItems);

            // 同一个message中工具调用相关的内容加到后面
            for (MessageContent content : contents) {
                if(content == null)
                    continue;

                String type = content.getType();

                switch (type) {
                case "tool_call":
                    processToolCallContent(content, message, conversationItems);
                    break;

                case "tool_result":
                    processToolResultContent(content, message, conversationItems);
                    break;
                }
            }

            // Process file attachments
            processFileAttachments(message, role, conversationItems);
        }

        return conversationItems;
    }

    private static void processReasoningContent(Message message, List<ConversationItem> conversationItems) {
        if (message.getReasoningContent() != null && !message.getReasoningContent().isEmpty()) {
            Reasoning reasoning = new Reasoning();
            reasoning.setContent(Lists.newArrayList(
                Reasoning.ReasoningText.builder()
                    .text(message.getReasoningContent())
                    .build()
            ));
            conversationItems.add(reasoning);
        }
    }

    private static void processTextContent(MessageContent content, String role,
                                          List<InputContent> inputContents,
                                          List<OutputContent> outputContents) {
        if (content.getText() != null && content.getText().getValue() != null) {
            if ("user".equals(role) || "system".equals(role) || "developer".equals(role)) {
                InputText inputText = new InputText();
                inputText.setText(content.getText().getValue());
                inputContents.add(inputText);
            } else if ("assistant".equals(role)) {
                OutputText outputText = new OutputText();
                outputText.setText(content.getText().getValue());
                outputText.setAnnotations(AnnotationUtils.convertToResponseAnnotations(content.getText().getAnnotations()));
                outputContents.add(outputText);
            }
        }
    }

    private static void processImageUrlContent(MessageContent content, String role,
                                              List<InputContent> inputContents) {
        if (content.getImageUrl() != null && "user".equals(role)) {
            InputImage inputImage = new InputImage();
            inputImage.setImageUrl(content.getImageUrl().getUrl());
            inputImage.setDetail(content.getImageUrl().getDetail());
            inputContents.add(inputImage);
        }
    }

    private static void processImageFileContent(MessageContent content, String role,
                                                List<InputContent> inputContents) {
        if (content.getImageFile() != null && "user".equals(role)) {
            InputImage inputImage = new InputImage();
            inputImage.setFileId(content.getImageFile().getFileId());
            inputImage.setDetail(content.getImageFile().getDetail());
            inputContents.add(inputImage);
        }
    }

    private static void processToolCallContent(MessageContent content, Message message,
                                              List<ConversationItem> conversationItems) {
        if (content.getToolCall() == null) return;

        ChatToolCall toolCall = content.getToolCall();
        String toolCallId = toolCall.getId();

        Map<String, String> mataData = message.getMetadata();
        // Get the item type from metadata to determine the correct tool call type
        String itemType = mataData.getOrDefault(toolCallId + "_type", mataData.get("item_type"));
        // Get item ID from message metadata if available
        String itemId = mataData.getOrDefault(toolCallId + "_id", mataData.get("item_id"));

        if ("function_call".equals(itemType)) {
            conversationItems.add(createFunctionToolCall(toolCall, toolCallId, itemId));
        } else if ("local_shell_call".equals(itemType)) {
            conversationItems.add(createLocalShellToolCall(toolCall, toolCallId, itemId));
        } else if ("custom_tool_call".equals(itemType)) {
            conversationItems.add(createCustomToolCall(toolCall, toolCallId, itemId));
        } else if ("file_search_call".equals(itemType)) {
            conversationItems.add(createFileSearchToolCall(toolCall, itemId));
        } else if ("web_search_call".equals(itemType)) {
            conversationItems.add(createWebSearchToolCall(toolCall, itemId));
        } else if ("image_generation_call".equals(itemType)) {
            conversationItems.add(createImageGenerationToolCall(itemId));
        } else if ("mcp_list_tools".equalsIgnoreCase(itemType)) {
            conversationItems.add(createMcpListTools(toolCall, itemId));
        } else if ("mcp_call".equalsIgnoreCase(itemType)) {
            conversationItems.add(createMcpToolCall(toolCall, itemId));
        } else if ("mcp_call_approval".equals(itemType)) {
            conversationItems.add(createMcpApprovalRequest(toolCall, itemId));
        }
    }

    private static FunctionToolCall createFunctionToolCall(ChatToolCall toolCall, String toolCallId, String itemId) {
        FunctionToolCall functionCall = new FunctionToolCall();
        functionCall.setCallId(toolCallId);
        functionCall.setName(toolCall.getFunction().getName());
        functionCall.setArguments(toolCall.getFunction().getArguments() != null
            ? toolCall.getFunction().getArguments().toString()
            : "{}");
        functionCall.setStatus(ItemStatus.COMPLETED);
        functionCall.setId(itemId);
        return functionCall;
    }

    private static LocalShellToolCall createLocalShellToolCall(ChatToolCall toolCall, String toolCallId, String itemId) {
        LocalShellToolCall localShellCall = new LocalShellToolCall();
        localShellCall.setCallId(toolCallId);

        // Parse action from arguments
        JsonNode arguments = toolCall.getFunction().getArguments();
        if (arguments != null) {
            localShellCall.setAction(JacksonUtils.deserialize(
                arguments.toString(),
                LocalShellToolCall.ShellAction.class
            ));
        }
        localShellCall.setStatus(ItemStatus.COMPLETED);
        localShellCall.setId(itemId);
        return localShellCall;
    }

    private static CustomToolCall createCustomToolCall(ChatToolCall toolCall, String toolCallId, String itemId) {
        CustomToolCall customCall = new CustomToolCall();
        customCall.setCallId(toolCallId);

        // Extract input from arguments
        JsonNode arguments = toolCall.getFunction().getArguments();
        if (arguments != null && arguments.has("input_data")) {
            customCall.setInput(arguments.get("input_data").asText());
        }
        customCall.setId(itemId);
        return customCall;
    }

    private static FileSearchToolCall createFileSearchToolCall(ChatToolCall toolCall, String itemId) {
        FileSearchToolCall fileSearchCall = new FileSearchToolCall();
        fileSearchCall.setId(itemId);
        fileSearchCall.setStatus(ItemStatus.COMPLETED);

        // Extract query from arguments
        JsonNode arguments = toolCall.getFunction() != null ? toolCall.getFunction().getArguments() : null;
        if (arguments != null && arguments.has("query")) {
            fileSearchCall.setQueries(Lists.newArrayList(arguments.get("query").asText()));
        }

        return fileSearchCall;
    }

    private static WebSearchToolCall createWebSearchToolCall(ChatToolCall toolCall, String itemId) {
        WebSearchToolCall webSearchCall = new WebSearchToolCall();
        webSearchCall.setId(itemId);
        webSearchCall.setStatus(ItemStatus.COMPLETED);

        // Extract query from arguments and create SearchAction
        JsonNode arguments = toolCall.getFunction() != null ? toolCall.getFunction().getArguments() : null;
        if (arguments != null && arguments.has("query")) {
            WebSearchToolCall.SearchAction searchAction = new WebSearchToolCall.SearchAction();
            searchAction.setQuery(arguments.get("query").asText());
            webSearchCall.setAction(searchAction);
        }

        return webSearchCall;
    }

    private static ImageGenerationToolCall createImageGenerationToolCall(String itemId) {
        ImageGenerationToolCall imageGenCall = new ImageGenerationToolCall();
        imageGenCall.setId(itemId);
        imageGenCall.setStatus(ItemStatus.COMPLETED);
        imageGenCall.setDataType("url");
        return imageGenCall;
    }

    private static MCPListTools createMcpListTools(ChatToolCall toolCall, String itemId) {
        MCPListTools mcpListTools = new MCPListTools();
        mcpListTools.setId(itemId);
        mcpListTools.setServerLabel(toolCall.getFunction() != null ? toolCall.getFunction().getName() : "");

        // Extract tools list from arguments
        JsonNode arguments = toolCall.getFunction() != null ? toolCall.getFunction().getArguments() : null;

        if (arguments != null) {
            if(arguments instanceof ArrayNode arrayNode) {
                // Parse array of tools
                List<MCPListTools.MCPToolInfo> tools = new ArrayList<>();
                for (JsonNode node : arrayNode) {
                    MCPListTools.MCPToolInfo toolInfo = JacksonUtils.deserialize(
                        node.toString(),
                        MCPListTools.MCPToolInfo.class
                    );
                    if (toolInfo != null) {
                        tools.add(toolInfo);
                    }
                }
                mcpListTools.setTools(tools);
            } else if(arguments instanceof TextNode) {
                // Text node indicates error
                mcpListTools.setError(arguments.asText());
            }
        }

        return mcpListTools;
    }


    private static MCPToolCall createMcpToolCall(ChatToolCall toolCall, String itemId) {
        MCPToolCall mcpToolCall = new MCPToolCall();
        mcpToolCall.setId(itemId);

        // Extract tool name and server label from function name
        // Format is typically: serverLabel_toolName
        String functionName = toolCall.getFunction() != null ? toolCall.getFunction().getName() : "";
        String[] strs = functionName.split("_", 2);
        if (strs.length == 2) {
            mcpToolCall.setServerLabel(strs[0]);
            mcpToolCall.setName(strs[1]);
        } else {
            mcpToolCall.setServerLabel(functionName);
            mcpToolCall.setName(functionName);
        }

        // Set arguments as JSON string
        JsonNode arguments = toolCall.getFunction() != null ? toolCall.getFunction().getArguments() : null;
        if (arguments != null) {
            mcpToolCall.setArguments(arguments.toString());
        } else {
            mcpToolCall.setArguments("{}");
        }

        return mcpToolCall;
    }

    private static MCPApprovalRequest createMcpApprovalRequest(ChatToolCall toolCall, String itemId) {
        MCPApprovalRequest approvalRequest = new MCPApprovalRequest();
        approvalRequest.setId(itemId);

        String functionName = toolCall.getFunction() != null ? toolCall.getFunction().getName() : "";
        String[] strs = functionName.split("_", 2);
        if (strs.length == 2) {
            approvalRequest.setServerLabel(strs[0]);
            if(strs[1].length() > 9) {
                approvalRequest.setName(strs[1].substring(0, strs[1].length() - 9));
            }
        } else {
            approvalRequest.setServerLabel(functionName);
            approvalRequest.setName(functionName);
        }

        // Set arguments as JSON string
        JsonNode arguments = toolCall.getFunction() != null ? toolCall.getFunction().getArguments() : null;
        if (arguments != null) {
            approvalRequest.setArguments(arguments.toString());
        } else {
            approvalRequest.setArguments("{}");
        }

        return approvalRequest;
    }

    @SuppressWarnings("unchecked")
    private static void processToolResultContent(MessageContent content, Message message,
                                                List<ConversationItem> conversationItems) {
        if (content.getToolResult() == null) return;

        ToolMessage toolResult = content.getToolResult();
        String toolCallId = toolResult.getToolCallId();
        String output = toolResult.getContent();

        Map<String, String> mataData = message.getMetadata();
        // Get the item type from metadata to determine the correct tool call type
        String itemType = mataData.getOrDefault(toolCallId + "_type", mataData.get("item_type"));
        // Get item ID from message metadata if available
        String itemId = mataData.getOrDefault(toolCallId + "_id", mataData.get("item_id"));

        if ("function_call_output".equals(itemType)) {
            FunctionToolCallOutput functionOutput = new FunctionToolCallOutput();
            functionOutput.setCallId(toolCallId);
            functionOutput.setOutput(output);
            conversationItems.add(functionOutput);
        } else if ("local_shell_call_output".equals(itemType)) {
            LocalShellCallOutput localShellOutput = new LocalShellCallOutput();
            localShellOutput.setId(toolCallId);
            localShellOutput.setOutput(output);
            localShellOutput.setStatus(ItemStatus.COMPLETED);
            conversationItems.add(localShellOutput);
        } else if ("custom_tool_call_output".equals(itemType)) {
            CustomToolCallOutput customOutput = new CustomToolCallOutput();
            customOutput.setCallId(toolCallId);
            customOutput.setOutput(output);
            conversationItems.add(customOutput);
        } else if ("file_search_call".equals(itemType) || "web_search_call".equals(itemType) || "image_generation_call".equals(itemType)
        || "mcp_list_tools".equals(itemType) || "mcp_call".equals(itemType)) {
            ConversationItem previousItem = findPreviousToolCall(conversationItems, itemId);
            Map<String, Object> map = JacksonUtils.toMap(output);
            if(map == null) return;
            if(previousItem instanceof FileSearchToolCall toolCall) {
                List<FileSearchToolCall.SearchResult> searchResults;
                if(map.containsKey("message")) {
                    searchResults = JacksonUtils.deserialize((String) map.get("message"), new TypeReference<>() {});
                } else {
                    FileSearchToolCall.SearchResult searchResult = new FileSearchToolCall.SearchResult();
                    searchResult.setText((String) map.getOrDefault("error", "no_message"));
                    searchResults = Lists.newArrayList(searchResult);
                }
                if(searchResults == null) {
                    searchResults = new ArrayList<>();
                    FileSearchToolCall.SearchResult result = new FileSearchToolCall.SearchResult();
                    result.setText(output);
                    searchResults.add(result);
                }
                toolCall.setResults(searchResults);
            } else {
                String data = (String) map.getOrDefault("message", map.getOrDefault("error", "no_message"));
                if(previousItem instanceof ImageGenerationToolCall toolCall) {
                    toolCall.setResult(data);
                } else if(previousItem instanceof MCPListTools toolCall) {
                    toolCall.setTools(JacksonUtils.deserialize(data, new TypeReference<>() {}));
                } else if(previousItem instanceof MCPToolCall toolCall) {
                    toolCall.setOutput(data);
                }
            }
        }
    }

    private static void addAccumulatedContents(String role, List<InputContent> inputContents,
                                              List<OutputContent> outputContents,
                                              List<ConversationItem> conversationItems) {
        if (!inputContents.isEmpty()) {
            InputMessage inputMessage = new InputMessage();
            inputMessage.setRole(getMessageRole(role));

            if (inputContents.size() == 1 && inputContents.get(0) instanceof InputText inputText) {
                // Single text content - use string form
                inputMessage.setContent(InputContentValue.of(inputText.getText()));
            } else {
                // Multiple contents - use array form
                inputMessage.setContent(InputContentValue.of(inputContents));
            }

            conversationItems.add(inputMessage);
        }

        if (!outputContents.isEmpty()) {
            OutputMessage outputMessage = new OutputMessage();
            outputMessage.setRole(MessageRole.ASSISTANT);
            outputMessage.setStatus(ItemStatus.COMPLETED);

            if (outputContents.size() == 1 && outputContents.get(0) instanceof OutputText outputText && CollectionUtils.isEmpty(outputText.getAnnotations())) {
                // Single text content - use string form
                outputMessage.setContent(OutputContentValue.of(outputText.getText()));
            } else {
                // Multiple contents - use array form
                outputMessage.setContent(OutputContentValue.of(outputContents));
            }

            conversationItems.add(outputMessage);
        }
    }

    private static void processFileAttachments(Message message, String role,
                                              List<ConversationItem> conversationItems) {
        if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
            for (Attachment attachment : message.getAttachments()) {
                if (attachment.getFileId() != null) {
                    InputFile inputFile = new InputFile();
                    inputFile.setFileId(attachment.getFileId());

                    InputMessage fileMessage = new InputMessage();
                    fileMessage.setRole(getMessageRole(role));
                    fileMessage.setContent(InputContentValue.of(Lists.newArrayList(inputFile)));

                    conversationItems.add(fileMessage);
                }
            }
        }
    }

    private static ConversationItem findPreviousToolCall(List<ConversationItem> items, String itemId) {
        if(itemId == null) {
            return null;
        }
        for (ConversationItem item : items) {
            if (item instanceof ToolCall call) {
                if (itemId.equals(call.getId())) {
                    return call;
                }
            }
        }
        return null;
    }

    private static MessageRole getMessageRole(String role) {
        if (role == null) {
            return MessageRole.USER;
        }
        switch (role.toLowerCase()) {
            case "assistant":
                return MessageRole.ASSISTANT;
            case "system":
                return MessageRole.SYSTEM;
            case "developer":
                return MessageRole.DEVELOPER;
            case "user":
            default:
                return MessageRole.USER;
        }
    }

    public static String converterToToolCallItemType(Tool tool) {
        if(tool instanceof Tool.Function) {
            return "function_call";
        }
        if(tool instanceof Tool.LocalShell) {
            return "local_shell_call";
        }
        if(tool instanceof Tool.Custom) {
            return "custom_tool_call";
        }
        if(tool instanceof Tool.Retrieval) {
            return "file_search_call";
        }
        if(tool instanceof Tool.WebSearch || tool instanceof Tool.WebSearchTavily) {
            return "web_search_call";
        }
        if(tool instanceof Tool.ImgGenerate) {
            return "image_generation_call";
        }
        if(tool instanceof Tool.MCP) {
            return "mcp_list_tools";
        }
        return "unknown";
    }

    public static String converterToToolOutputItemType(Tool tool) {
        if(tool instanceof Tool.Function) {
            return "function_call_output";
        }
        if(tool instanceof Tool.LocalShell) {
            return "local_shell_call_output";
        }
        if(tool instanceof Tool.Custom) {
            return "custom_tool_call_output";
        }
        return converterToToolCallItemType(tool);
    }
}
