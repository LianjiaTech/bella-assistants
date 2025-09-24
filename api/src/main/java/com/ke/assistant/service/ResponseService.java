package com.ke.assistant.service;

import com.google.common.collect.Lists;
import com.ke.assistant.core.run.RunStatus;
import com.ke.assistant.db.IdGenerator;
import com.ke.assistant.db.generated.tables.pojos.ResponseIdMappingDb;
import com.ke.assistant.db.generated.tables.pojos.ThreadDb;
import com.ke.assistant.db.repo.ResponseIdMappingRepo;
import com.ke.assistant.model.ResponseCreateResult;
import com.ke.assistant.model.RunCreateResult;
import com.ke.assistant.util.BeanUtils;
import com.ke.assistant.util.MessageUtils;
import com.ke.assistant.util.MetaConstants;
import com.ke.assistant.util.ToolUtils;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.assistants.assistant.FunctionResources;
import com.theokanning.openai.assistants.assistant.Tool;
import com.theokanning.openai.assistants.assistant.ToolResources;
import com.theokanning.openai.assistants.message.Message;
import com.theokanning.openai.assistants.message.MessageContent;
import com.theokanning.openai.assistants.message.content.ImageFile;
import com.theokanning.openai.assistants.message.content.Text;
import com.theokanning.openai.assistants.run.AllowedTools;
import com.theokanning.openai.assistants.run.Run;
import com.theokanning.openai.assistants.run.RunCreateRequest;
import com.theokanning.openai.assistants.run.ToolChoice;
import com.theokanning.openai.assistants.run_step.RunStep;
import com.theokanning.openai.assistants.run_step.StepDetails;
import com.theokanning.openai.assistants.thread.Attachment;
import com.theokanning.openai.assistants.thread.Thread;
import com.theokanning.openai.assistants.thread.ThreadRequest;
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
import com.theokanning.openai.response.Response;
import com.theokanning.openai.response.ResponseStatus;
import com.theokanning.openai.response.ToolChoiceValue;
import com.theokanning.openai.response.content.InputAudio;
import com.theokanning.openai.response.content.InputContent;
import com.theokanning.openai.response.content.InputFile;
import com.theokanning.openai.response.content.InputImage;
import com.theokanning.openai.response.content.InputMessage;
import com.theokanning.openai.response.content.InputText;
import com.theokanning.openai.response.content.OutputContent;
import com.theokanning.openai.response.content.OutputMessage;
import com.theokanning.openai.response.content.OutputText;
import com.theokanning.openai.response.content.Reasoning;
import com.theokanning.openai.response.content.Refusal;
import com.theokanning.openai.response.tool.ComputerToolCall;
import com.theokanning.openai.response.tool.CustomToolCall;
import com.theokanning.openai.response.tool.FunctionToolCall;
import com.theokanning.openai.response.tool.LocalShellToolCall;
import com.theokanning.openai.response.tool.ToolCall;
import com.theokanning.openai.response.tool.output.ComputerToolCallOutput;
import com.theokanning.openai.response.tool.output.CustomToolCallOutput;
import com.theokanning.openai.response.tool.output.FunctionToolCallOutput;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Response API Service Handles Response API business logic
 */
@Service
@Slf4j
public class ResponseService {

    @Autowired
    private ThreadLockService threadLockService;

    @Autowired
    private ThreadService threadService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private RunService runService;

    @Autowired
    private IdGenerator idGenerator;

    @Autowired
    private ResponseIdMappingRepo responseIdMappingRepo;

    /**
     * Create a new response
     */
    public ResponseCreateResult createResponse(CreateResponseRequest request) {
        List<Tool> tools = ToolUtils.convertFromToolDefinition(request.getTools());
        ToolResources toolResources = null;
        Tool.Retrieval retrieval = tools.stream().filter(tool -> tool instanceof Tool.Retrieval).map(tool -> (Tool.Retrieval) tool).findAny().orElse(null);
        if(retrieval != null) {
            toolResources = new ToolResources();
            Assert.notEmpty(retrieval.getFileIds(), "vector_store_ids can not be null.");
            FunctionResources functionResources = new FunctionResources();
            functionResources.setName(retrieval.getType());
            functionResources.setFileIds(retrieval.getFileIds());
            toolResources.setFunctions(Lists.newArrayList(functionResources));
        }

        Map<String, Tool> inheritTolls = new HashMap<>();

        // Determine thread handling
        boolean isNewThread = false;
        String threadId = null;
        String previousRunId = null;
        String previousThreadId = null;

        // 确定threadId
        if(request.getConversation() != null && request.getConversation().getStringValue() != null) {
            // Use existing conversation/thread
            threadId = request.getConversation().getStringValue();
            if(threadId == null) {
                threadId = request.getConversation().getObjectValue().getId();
            }
            Thread thread = threadService.getThreadById(threadId);
            Assert.notNull(thread, "conversation is not exist");
        } else if(request.getPreviousResponseId() != null) {
            // Fork from previous response - get thread from run mapping
            ResponseIdMappingDb db = responseIdMappingRepo.findByResponseId(request.getPreviousResponseId());
            Assert.notNull(db, "previous_id is not exist");
            Run previousRun = runService.getRunById(db.getThreadId(), db.getRunId());
            threadId = previousRun.getThreadId();
            previousRunId = previousRun.getId();
            previousThreadId = previousRun.getThreadId();
            if(CollectionUtils.isNotEmpty(previousRun.getTools())) {
                previousRun.getTools().forEach(tool -> inheritTolls.put(tool.getType(), tool));
            }
        }

        // Create new thread if needed
        if(threadId == null) {
            Thread thread = createThreadFromRequest(request);
            threadId = thread.getId();
            isNewThread = true;
        }

        // Convert input to messages
        List<Message> messages = checkAndConvertInputToMessages(threadId, request);

        List<Message> inputMessages = new ArrayList<>();
        StringBuilder systemPrompt = new StringBuilder();

        for(Message message : messages) {
            if(message.getRole().equals("system") || message.getRole().equals("developer")) {
                message.getContent().stream().map(MessageContent::getText)
                        .filter(Objects::nonNull)
                        .map(Text::getValue)
                        .filter(Objects::nonNull)
                        .forEach(text -> {
                            if(systemPrompt.length() > 0) {
                                systemPrompt.append("\n");
                            }
                            systemPrompt.append(text);
                        });

            } else {
                inputMessages.add(message);
            }
        }

        // 检查第一条消息是否合法
        if(!inputMessages.isEmpty() && previousRunId != null) {
            List<RunStep> runSteps = runService.getRunSteps(previousThreadId, previousRunId);
            if(CollectionUtils.isNotEmpty(runSteps)) {
                Message preMessage = null;
                for(RunStep runStep : runSteps) {
                    RunStatus runStatus = RunStatus.fromValue(runStep.getStatus());
                    if(runStep.getType().equals("tool_calls")) {
                        if(runStatus == RunStatus.REQUIRES_ACTION) {
                            //查找需要提交工具结果的tool_call
                            List<com.theokanning.openai.assistants.run.ToolCall> toolCalls = runStep.getStepDetails().getToolCalls().stream()
                                    .filter(toolCall -> toolCall.getFunction() != null && toolCall.getFunction().getOutput() == null)
                                    .collect(Collectors.toList());
                            if(!toolCalls.isEmpty()) {
                                preMessage = new Message();
                                preMessage.setRole("assistant");
                                preMessage.setContent(new ArrayList<>());
                                for (com.theokanning.openai.assistants.run.ToolCall toolCall : toolCalls) {
                                    MessageContent messageContent = new MessageContent();
                                    messageContent.setType("tool_call");
                                    messageContent.setToolCall(MessageUtils.convertToChatToolCall(toolCall));
                                    preMessage.getContent().add(messageContent);
                                }
                                break;
                            }
                        }
                    } else {
                        if(runStatus.isTerminal()) {
                            String messageId = runStep.getStepDetails().getMessageCreation().getMessageId();
                            preMessage = messageService.getMessageById(previousThreadId, messageId);
                            break;
                        }
                    }
                }

                MessageUtils.checkPre(preMessage, inputMessages.get(0));
            }
        }

        List<Attachment> attachments = inputMessages.stream().map(Message::getAttachments)
                .flatMap(List::stream).collect(Collectors.toList());

        Map<String, Tool> attachmentTools = attachments.stream().map(Attachment::getTools)
                .flatMap(List::stream).collect(Collectors.toMap(Tool::getType, Function.identity(),
                        (existing, replacement) -> existing));

        // 附件需要的方法
        if(!attachmentTools.isEmpty()) {
            tools.forEach(tool -> {
                if(attachmentTools.remove(tool.getType()) != null) {
                    tool.toInherit();
                }
            });
            tools.addAll(attachmentTools.values());
        }

        // 需要从之前的run中继承的方法
        if(!inheritTolls.isEmpty()) {
            tools.forEach(tool -> {
                if(inheritTolls.remove(tool.getType()) != null) {
                    tool.toInherit();
                }
            });
            tools.addAll(inheritTolls.values());
        }

        String responseId = idGenerator.generateResponseId();
        String runId = idGenerator.generateRunId();

        if(Boolean.FALSE != request.getStore()) {
            ResponseIdMappingDb db = checkAndStore(responseId, threadId, runId, request.getPreviousResponseId(), request.getUser());
            threadId = db.getThreadId(); // fork后threadId可能变化
        }

        // Create run request
        RunCreateRequest runCreateRequest = new RunCreateRequest();
        Map<String, String> metadata = new HashMap<>();
        runCreateRequest.setAssistantId(null);
        runCreateRequest.setModel(request.getModel());
        runCreateRequest.setInstructions(request.getInstructions());
        runCreateRequest.setAdditionalInstructions(systemPrompt.length() > 0 ? systemPrompt.toString() : null);
        runCreateRequest.setTools(tools);
        runCreateRequest.setToolChoice(convertToolChoice(request.getToolChoice()));
        runCreateRequest.setUser(runCreateRequest.getUser());

        runCreateRequest.setTemperature(request.getTemperature());
        runCreateRequest.setTopP(request.getTopP());
        runCreateRequest.setMaxCompletionTokens(request.getMaxOutputTokens());
        runCreateRequest.setTruncationStrategy(request.getTruncationStrategy());
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

        // Create run
        RunCreateResult runResult = runService.createRun(threadId, runId, runCreateRequest, attachments, inputMessages, toolResources);

        // Build response object
        Response response = buildResponseFromRun(runResult.getRun(), responseId);

        return ResponseCreateResult.builder()
                .response(response)
                .threadId(threadId)
                .runId(runResult.getRun().getId())
                .responseId(responseId)
                .assistantMessageId(runResult.getAssistantMessageId())
                .additionalMessages(runResult.getAdditionalMessages())
                .isNewThread(isNewThread)
                .build();
    }

    private ResponseIdMappingDb checkAndStore(String responseId, String threadId, String runId, String previousResponseId, String user) {
        if(previousResponseId == null) {
            return store(responseId, threadId,  runId, "", user);
        }
        return threadLockService.executeWithWriteLock(previousResponseId, () -> {
            ResponseIdMappingDb exist = responseIdMappingRepo.findByPreviousResponseId(previousResponseId);
            if(exist != null) {
                //todo: fork this thread with the last run included steps
                throw new BizParamCheckException("This response is only supported for run with the once time");
            }
            return store(responseId, threadId, runId, previousResponseId, user);
        });
    }

    private ResponseIdMappingDb store(String responseId, String threadId, String runId, String previousResponseId, String user) {
        // Create response-to-run mapping for concurrency control
        ResponseIdMappingDb mappingDb = new ResponseIdMappingDb();
        mappingDb.setResponseId(responseId);
        mappingDb.setRunId(runId);
        mappingDb.setPreviousResponseId(previousResponseId);
        mappingDb.setThreadId(threadId);
        mappingDb.setUser(user);
        return responseIdMappingRepo.insert(mappingDb);
    }

    private Thread createThreadFromRequest(CreateResponseRequest request) {
        ThreadRequest threadRequest = new ThreadRequest();

        if(request.getConversation() != null && request.getConversation().getObjectValue() != null) {
            // Use conversation object data
            threadRequest.setMetadata(request.getConversation().getObjectValue().getMetadata());
        }

        // Set thread metadata
        Map<String, String> threadMetadata = new HashMap<>();
        threadMetadata.put("created_from_response", "true");
        if(request.getMetadata() != null) {
            threadMetadata.putAll(request.getMetadata());
        }
        threadRequest.setMetadata(threadMetadata);

        ThreadDb threadDb = new ThreadDb();
        BeanUtils.copyProperties(threadRequest, threadDb);
        threadDb.setUser(BellaContext.getOwnerCode());
        threadDb.setOwner(BellaContext.getOwnerCode());

        if(threadRequest.getMetadata() != null) {
            threadDb.setMetadata(JacksonUtils.serialize(threadRequest.getMetadata()));
        }

        return threadService.createThread(threadDb, null, new ArrayList<>());
    }


    private Response buildResponseFromRun(Run run, String responseId) {
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

        // Add conversation reference
        ConversationValue conversationValue = ConversationValue.of(run.getThreadId());
        builder.conversation(conversationValue);

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


    private List<Message> checkAndConvertInputToMessages(String threadId, CreateResponseRequest request) {
        List<Message> messages = new ArrayList<>();
        if(request.getInput() != null) {
            if(request.getInput().getInputType() == InputValue.InputType.STRING) {
                Message message = new Message();
                message.setRole("user");
                message.setContent(Lists.newArrayList(textContent(request.getInput().getStringValue())));
                messages.add(message);
            } else if(request.getInput().getInputType() == InputValue.InputType.OBJECT_LIST) {
                messages = convertConversationItemsToMessages(threadId, request.getInput().getObjectListValue());
            }
        }
        for(int i = 1; i < messages.size(); i++) {
            MessageUtils.checkPre(messages.get(i-1), messages.get(i));
        }
        return messages;
    }

    private List<Message> convertConversationItemsToMessages(String threadId, List<ConversationItem> conversationItems) {
        Map<String, List<String>> runStepToolCallIds = new HashMap<>();
        Map<String, ChatToolCall> toolCallMap = new HashMap<>();
        Map<String, ToolMessage> toolResultMap = new HashMap<>();
        for(ConversationItem conversationItem : conversationItems) {
            if(conversationItem instanceof ToolCall) {
                if(conversationItem instanceof FunctionToolCall) {
                    continue;
                }
                ToolCall toolCall = (ToolCall) conversationItem;
                String id = toolCall.getId();
                Assert.hasText(id, "Id is required with tool call: " + toolCall.getType());
                String[] ids = id.split("_");
                if(ids.length <= 2) {
                    throw new BizParamCheckException("invalid id with tool call: " + toolCall.getType());
                }
                String stepId = ids[0].concat("_").concat(ids[1]);
                String toolCallId = ids[2];
                for(int i = 3; i < ids.length; i++) {
                    toolCallId = toolCallId.concat("_").concat(ids[i]);
                }
                runStepToolCallIds.computeIfAbsent(stepId, key -> new ArrayList<>()).add(toolCallId);
            }
        }
        if(!runStepToolCallIds.isEmpty()) {
            List<RunStep> runSteps = runService.getRunSteps(threadId, Lists.newArrayList(runStepToolCallIds.keySet()));
            for(RunStep runStep : runSteps) {
                StepDetails stepDetails = runStep.getStepDetails();
                Map<String, com.theokanning.openai.assistants.run.ToolCall> stepToolCalls = stepDetails.getToolCalls().stream().collect(Collectors.toMap(com.theokanning.openai.assistants.run.ToolCall::getId, t -> t));
                List<String> toolCallIds = runStepToolCallIds.get(runStep.getId());
                for(String tooCallId : toolCallIds) {
                    com.theokanning.openai.assistants.run.ToolCall toolCall = stepToolCalls.get(tooCallId);
                    Assert.notNull(toolCall, "invalid tool call id");
                    String id = runStep.getId().concat("_").concat(tooCallId);
                    toolCallMap.put(id, MessageUtils.convertToChatToolCall(toolCall));
                    toolResultMap.put(id, MessageUtils.convertToToolMessage(toolCall, runStep.getLastError()));
                }
            }
        }
        List<Message> messages = new ArrayList<>();
        messages.add(message());
        for(ConversationItem conversationItem : conversationItems) {
            Message last = messages.get(messages.size() - 1);
            if(conversationItem instanceof InputMessage) {
                InputMessage inputMessage = (InputMessage) conversationItem;
                String role = inputMessage.getRole().getValue();
                Message message = setRoleOrNewMessage(last, role, messages);
                if(inputMessage.getContent().isString()) {
                    message.getContent().add(textContent(inputMessage.getContent().getStringValue()));
                } else {
                    for(InputContent inputContent : inputMessage.getContent().getArrayValue()) {
                        if(inputContent instanceof InputText) {
                            InputText inputText = (InputText) inputContent;
                            message.getContent().add(textContent(inputText.getText()));
                        } else if(inputContent instanceof InputImage) {
                            InputImage inputImage = (InputImage) inputContent;
                            MessageContent messageContent = new MessageContent();
                            if(inputImage.getImageUrl() != null) {
                                messageContent.setImageUrl(new ImageUrl(inputImage.getImageUrl(), inputImage.getDetail()));
                            } else {
                                messageContent.setImageFile(new ImageFile(inputImage.getFileId(), inputImage.getDetail()));
                            }
                        } else if(inputContent instanceof InputFile) {
                            InputFile inputFile = (InputFile) inputContent;

                            message.getAttachments().add(new Attachment(inputFile.getFileId(), Lists.newArrayList(new Tool.Retrieval(true, true), new Tool.ReadFiles(true, true))));
                        } else if(inputContent instanceof InputAudio) {
                            // todo: support InputAudio
                        }
                    }
                }
            } else if(conversationItem instanceof OutputMessage) {
                OutputMessage outputMessage = (OutputMessage) conversationItem;
                String role = outputMessage.getRole().getValue();
                Message message = setRoleOrNewMessage(last, role, messages);
                if(outputMessage.getContent().isString()) {
                    message.getContent().add(textContent(outputMessage.getContent().getStringValue()));
                } else {
                    for(OutputContent outputContent : outputMessage.getContent().getArrayValue()) {
                        if(outputContent instanceof OutputText) {
                            OutputText outputText = (OutputText) outputContent;
                            message.getContent().add(textContent(outputText.getText()));
                        } else if(outputContent instanceof Refusal) {
                            Refusal refusal = (Refusal) outputContent;
                            message.getContent().add(textContent(refusal.getRefusal()));
                        }
                    }
                }
            } else if(conversationItem instanceof FunctionToolCall) {
                Message message = setRoleOrNewMessage(last, "assistant", messages);
                MessageContent content = new MessageContent();
                content.setType("tool_call");
                FunctionToolCall call = (FunctionToolCall) conversationItem;
                ChatToolCall toolCall = new ChatToolCall();
                toolCall.setId(call.getCallId());
                toolCall.setType("function");
                ChatFunctionCall functionCall = new ChatFunctionCall();
                functionCall.setName(call.getName());
                functionCall.setArguments(JacksonUtils.deserialize(call.getArguments()));
                toolCall.setFunction(functionCall);
                content.setToolCall(toolCall);
                message.getContent().add(content);
            } else if(conversationItem instanceof FunctionToolCallOutput) {
                Message message = setRoleOrNewMessage(last, "tool", messages);
                message.setRole("tool");
                FunctionToolCallOutput functionToolCallOutput = (FunctionToolCallOutput) conversationItem;
                ToolMessage toolMessage = new ToolMessage();
                toolMessage.setToolCallId(functionToolCallOutput.getCallId());
                toolMessage.setContent(functionToolCallOutput.getOutput());
                MessageContent content = new MessageContent();
                content.setType("tool_result");
                content.setToolResult(toolMessage);
                message.getContent().add(content);
            } else if(conversationItem instanceof LocalShellToolCall) {

                LocalShellToolCall localShellToolCall = (LocalShellToolCall) conversationItem;

                Map<String, String> meta = new HashMap<>();
                meta.put("itemId", localShellToolCall.getId());

                // tool call
                ChatToolCall toolCall = toolCallMap.get(localShellToolCall.getId());
                Assert.notNull(toolCall, "invalid local tool call id");
                Message tooCallMessage = setRoleOrNewMessage(last, "assistant", messages);
                MessageContent toolCallContent = new MessageContent();
                toolCallContent.setType("tool_call");
                toolCallContent.setToolCall(toolCall);
                tooCallMessage.getContent().add(toolCallContent);
                tooCallMessage.setMetadata(meta);

                // tool result
                ToolMessage toolResult = new ToolMessage();
                toolResult.setToolCallId(localShellToolCall.getId());
                toolResult.setContent(localShellToolCall.getStatus() == ItemStatus.INCOMPLETE ? "Failed to run the local shell." : "Finish to run the local shell.");
                Message toolResultMessage = setRoleOrNewMessage(tooCallMessage, "tool", messages);
                MessageContent toolResultContent = new MessageContent();
                toolResultContent.setType("tool_result");
                toolResultContent.setToolResult(toolResult);
                toolResultMessage.getContent().add(toolResultContent);
                toolResultMessage.setMetadata(meta);
            } else if(conversationItem instanceof CustomToolCall) {
                CustomToolCall customToolCall = (CustomToolCall) conversationItem;
                Message message = setRoleOrNewMessage(last, "assistant", messages);
                MessageContent content = new MessageContent();
                content.setType("tool_call");
                ChatToolCall toolCall = new ChatToolCall();
                toolCall.setId(customToolCall.getCallId());
                toolCall.setType("function");
                ChatFunctionCall functionCall = new ChatFunctionCall();
                functionCall.setName(customToolCall.getName());
                Map<String, String> inputMap = new HashMap<>();
                inputMap.put("input_data", customToolCall.getInput());
                functionCall.setArguments(JacksonUtils.deserialize(JacksonUtils.serialize(inputMap)));
                toolCall.setFunction(functionCall);
                content.setToolCall(toolCall);
                message.getContent().add(content);
            } else if(conversationItem instanceof CustomToolCallOutput) {
                Message message = setRoleOrNewMessage(last, "tool", messages);
                message.setRole("tool");
                CustomToolCallOutput customToolCallOutput = (CustomToolCallOutput) conversationItem;
                ToolMessage toolMessage = new ToolMessage();
                toolMessage.setToolCallId(customToolCallOutput.getCallId());
                toolMessage.setContent(customToolCallOutput.getOutput());
                MessageContent content = new MessageContent();
                content.setType("tool_result");
                content.setToolResult(toolMessage);
                message.getContent().add(content);
            } else if(conversationItem instanceof ComputerToolCall) {
                //todo: 处理computer use
                throw new BizParamCheckException("can not supported ComputerUseToolCall");
            } else if(conversationItem instanceof ComputerToolCallOutput) {
                //todo: 处理computer use
                throw new BizParamCheckException("can not supported ComputerUseToolCallOutput");
            }
            else if(conversationItem instanceof ToolCall) {
                ToolCall toolCallItem = (ToolCall) conversationItem;
                ChatToolCall toolCall = toolCallMap.get(toolCallItem.getId());
                ToolMessage toolResult = toolResultMap.get(toolCallItem.getId());
                Assert.notNull(toolCall, "invalid tool call id");
                Assert.notNull(toolResult, "invalid tool call id");

                Map<String, String> meta = new HashMap<>();
                meta.put("itemId", toolCallItem.getId());

                // tool call
                Message tooCallMessage = setRoleOrNewMessage(last, "assistant", messages);
                MessageContent toolCallContent = new MessageContent();
                toolCallContent.setType("tool_call");
                toolCallContent.setToolCall(toolCall);
                tooCallMessage.getContent().add(toolCallContent);
                tooCallMessage.setMetadata(meta);
                // tool result
                Message toolResultMessage = setRoleOrNewMessage(tooCallMessage, "tool", messages);
                MessageContent toolResultContent = new MessageContent();
                toolResultContent.setType("tool_result");
                toolResultContent.setToolResult(toolResult);
                toolResultMessage.getContent().add(toolResultContent);
                toolResultMessage.setMetadata(meta);
            } else if(conversationItem instanceof Reasoning) {
                Message message = setRoleOrNewMessage(last, "assistant", messages);
                message.setRole("assistant");
                Reasoning reasoning = (Reasoning) conversationItem;
                if(reasoning.getSummary() != null) {
                    message.setReasoningContent(reasoning.getSummary().stream().map(Reasoning.SummaryText::getText).reduce(String::concat).orElse(""));
                }
                if(reasoning.getContent() != null) {
                    message.setReasoningContent((Optional.ofNullable(message.getReasoningContent()).orElse("")
                            .concat(reasoning.getContent().stream().map(Reasoning.ReasoningText::getText).reduce(String::concat).orElse(""))));
                }
            }
            else if(conversationItem instanceof ItemReference) {
                //todo: ItemReference
                throw new BizParamCheckException("can not support ItemReference");
            } else {
                throw new BizParamCheckException("unexpected input type");
            }
        }
        return messages;
    }


    private Message setRoleOrNewMessage(Message last, String role, List<Message> messages) {
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

    private Message message() {
        Message message = new Message();
        message.setContent(new ArrayList<>());
        message.setAttachments(new ArrayList<>());
        message.setMetadata(new HashMap<>());
        return message;
    }


    private MessageContent textContent(String data) {
        MessageContent content = new MessageContent();
        content.setType("text");
        Text text = new Text();
        text.setValue(data);
        text.setAnnotations(new ArrayList<>());
        content.setText(text);
        return content;
    }

    private ToolChoice convertToolChoice(ToolChoiceValue toolChoiceValue) {
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
}
