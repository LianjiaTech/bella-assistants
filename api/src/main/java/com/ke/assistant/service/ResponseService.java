package com.ke.assistant.service;

import com.google.common.collect.Lists;
import com.ke.assistant.core.run.RunStatus;
import com.ke.assistant.db.IdGenerator;
import com.ke.assistant.db.generated.tables.pojos.MessageDb;
import com.ke.assistant.db.generated.tables.pojos.ResponseIdMappingDb;
import com.ke.assistant.db.generated.tables.pojos.ThreadDb;
import com.ke.assistant.db.repo.ResponseIdMappingRepo;
import com.ke.assistant.model.ResponseCreateResult;
import com.ke.assistant.model.RunCreateResult;
import com.ke.assistant.util.MessageUtils;
import com.ke.assistant.util.ResponseUtils;
import com.ke.assistant.util.ToolUtils;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.common.exception.ResourceNotFoundException;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.assistants.assistant.FunctionResources;
import com.theokanning.openai.assistants.assistant.Tool;
import com.theokanning.openai.assistants.assistant.ToolResources;
import com.theokanning.openai.assistants.message.Message;
import com.theokanning.openai.assistants.message.MessageContent;
import com.theokanning.openai.assistants.message.content.Text;
import com.theokanning.openai.assistants.run.Run;
import com.theokanning.openai.assistants.run.RunCreateRequest;
import com.theokanning.openai.assistants.run_step.RunStep;
import com.theokanning.openai.assistants.thread.Attachment;
import com.theokanning.openai.assistants.thread.Thread;
import com.theokanning.openai.response.ConversationItem;
import com.theokanning.openai.response.CreateResponseRequest;
import com.theokanning.openai.response.Response;
import com.theokanning.openai.response.ResponseItem;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    @Autowired
    private ImageStorageService imageStorageService;

    @Autowired
    private AudioStorageService audioStorageService;

    /**
     * Create a new response
     */
    public ResponseCreateResult createResponse(CreateResponseRequest request) {
        if(Boolean.FALSE == request.getStore() && (request.getPreviousResponseId() != null || request.getConversation() != null)) {
            throw new BizParamCheckException("store can not be set `false` when you request with previous_response_id or conversation");
        }
        if(request.getPreviousResponseId() != null && request.getConversation() != null) {
            throw new BizParamCheckException("only one can be set with previous_response_id or conversation");
        }
        List<Tool> tools = ToolUtils.convertFromToolDefinition(request.getTools());
        ToolResources toolResources = toolResources(tools);

        List<Message> inputMessages = new ArrayList<>();
        StringBuilder systemPrompt = new StringBuilder();
        buildMessage(request, systemPrompt, inputMessages);

        List<Attachment> attachments = inputMessages.stream().map(Message::getAttachments)
                .flatMap(List::stream).collect(Collectors.toList());

        Map<String, Tool> inheritTools = new HashMap<>();
        Triple<String, String, String> triple = confirmThreadAndRun(request, inheritTools);
        String threadId = triple.getLeft();
        String previousThreadId = triple.getMiddle();
        String previousRunId = triple.getRight();

        checkFirstMessage(inputMessages, previousThreadId, previousRunId);
        addTools(tools, attachments, inheritTools);

        String responseId = idGenerator.generateResponseId();
        String runId = idGenerator.generateRunId();

        // create response entity
        ResponseIdMappingDb db = checkAndStore(responseId, threadId, runId, request.getPreviousResponseId(), request.getUser());
        threadId = db.getThreadId(); // fork后threadId可能变化

        String instructions = !systemPrompt.isEmpty() ? systemPrompt.toString() : null;
        RunCreateRequest runCreateRequest = ResponseUtils.convertToRunRequest(request, instructions, tools,
                responseId, threadId, previousThreadId, previousRunId);

        // Create run
        RunCreateResult runResult = runService.createRun(threadId, runId, runCreateRequest, attachments, inputMessages, toolResources);

        // Build response object
        Response response = ResponseUtils.buildResponseFromRun(runResult.getRun(), responseId);

        return ResponseCreateResult.builder()
                .response(response)
                .threadId(threadId)
                .runId(runResult.getRun().getId())
                .responseId(responseId)
                .assistantMessageId(runResult.getAssistantMessageId())
                .additionalMessages(runResult.getAdditionalMessages())
                .isNewThread(threadId.equals(previousThreadId))
                .build();
    }

    /**
     * get responses execution result
     */
    public Response getResponse(String responseId) {
        // Get response mapping from database
        ResponseIdMappingDb mappingDb = responseIdMappingRepo.findByResponseId(responseId);
        if (mappingDb == null) {
            throw new ResourceNotFoundException("Response not found: " + responseId);
        }

        // Get the run from database
        Run run = runService.getRunById(mappingDb.getThreadId(), mappingDb.getRunId());
        if (run == null) {
            throw new ResourceNotFoundException("Run not found for response: " + responseId);
        }

        Map<String, Tool> toolMap = run.getTools() == null ? new HashMap<>() :
                run.getTools().stream().collect(Collectors.toMap(tool -> {
                            if(tool instanceof Tool.MCP mcp) {
                                return mcp.getDefinition().getServerLabel();
                            }
                            if(tool instanceof Tool.Custom custom) {
                                return custom.getDefinition().getName();
                            }
                            return tool.getType();
                        },
                        Function.identity(),
                        (existing, replacement) -> existing));

        // Build basic response from run
        Response response = ResponseUtils.buildResponseFromRun(run, responseId);

        // If run is completed or failed, add output messages
        RunStatus status = RunStatus.fromValue(run.getStatus());
        if (status.isStopExecution()) {
            // Get all run steps to extract messages
            List<RunStep> runSteps = runService.getRunSteps(mappingDb.getThreadId(), mappingDb.getRunId());

            // Collect all messages from run steps
            List<Message> messages = new ArrayList<>();
            Message assistantMessage = null;
            for (RunStep runStep : runSteps) {
                RunStatus runStepStatus = RunStatus.fromValue(runStep.getStatus());
                if(!runStepStatus.isStopExecution()) {
                    continue;
                }
                if ("message_creation".equals(runStep.getType())) {
                    // Get the assistant message created in this step
                    if (runStep.getStepDetails() != null &&
                        runStep.getStepDetails().getMessageCreation() != null) {
                        String messageId = runStep.getStepDetails().getMessageCreation().getMessageId();
                        assistantMessage = messageService.getMessageById(mappingDb.getThreadId(), messageId);
                    }
                } else if ("tool_calls".equals(runStep.getType())) {
                    processToolCallSteps(runStep, toolMap, messages);
                }
            }

            if(assistantMessage != null) {
                messages.add(assistantMessage);
            }

            // Convert messages to conversation items (output)
            if (!messages.isEmpty()) {
                List<ConversationItem> conversationItems = ResponseUtils.convertMessagesToConversationItems(messages);

                // Filter to only include ResponseItems (output items)
                List<ResponseItem> outputItems = conversationItems.stream()
                    .filter(item -> item instanceof ResponseItem)
                    .map(item -> (ResponseItem) item)
                    .collect(Collectors.toList());

                response.setOutput(outputItems);
            }
        }

        return response;
    }

    private ToolResources toolResources(List<Tool> tools) {
        Tool.Retrieval retrieval = tools.stream().filter(tool -> tool instanceof Tool.Retrieval).map(tool -> (Tool.Retrieval) tool).findAny().orElse(null);
        if(retrieval != null) {
            ToolResources toolResources = new ToolResources();
            Assert.notEmpty(retrieval.getFileIds(), "vector_store_ids can not be null.");
            FunctionResources functionResources = new FunctionResources();
            functionResources.setName(retrieval.getType());
            functionResources.setFileIds(retrieval.getFileIds());
            toolResources.setFunctions(Lists.newArrayList(functionResources));
            return toolResources;
        }
        return null;
    }

    private void buildMessage(CreateResponseRequest request, StringBuilder systemPrompt, List<Message> inputMessages) {
        List<Message> messages = ResponseUtils.checkAndConvertInputToMessages(
                request,
                runService::getRunStep,
                imageStorageService::processImageUrl,
                audioStorageService::uploadAudio
        );
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
    }

    private Triple<String, String, String> confirmThreadAndRun(CreateResponseRequest request, Map<String, Tool> inheritTools) {
        String threadId = null;
        String previousRunId = null;
        String previousThreadId = null;
        // 确定threadId
        if(request.getConversation() != null) {
            // Use existing conversation/thread
            if(request.getConversation().isString()) {
                threadId = request.getConversation().getStringValue();
            } else {
                threadId = request.getConversation().getObjectValue().getId();
            }
            Assert.hasText(threadId, "invalid conversation value");
            Thread thread = threadService.getThreadById(threadId);
            Assert.notNull(thread, "conversation is not exist");
        } else if(request.getPreviousResponseId() != null) {
            ResponseIdMappingDb db = responseIdMappingRepo.findByResponseId(request.getPreviousResponseId());
            Assert.notNull(db, "previous_id is not exist");
            Run previousRun = runService.getRunById(db.getThreadId(), db.getRunId());
            threadId = previousRun.getThreadId();
            previousRunId = previousRun.getId();
            previousThreadId = previousRun.getThreadId();
            if(CollectionUtils.isNotEmpty(previousRun.getTools())) {
                previousRun.getTools().forEach(tool -> inheritTools.put(tool.getType(), tool));
            }
        }

        // Create new thread if needed
        if(threadId == null) {
            Thread thread = createThreadFromRequest(request);
            threadId = thread.getId();
        }

        return Triple.of(threadId, previousThreadId, previousRunId);
    }

    private void addTools(List<Tool> tools, List<Attachment> attachments, Map<String, Tool> inheritTools) {
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
        if(!inheritTools.isEmpty()) {
            tools.forEach(tool -> {
                if(inheritTools.remove(tool.getType()) != null) {
                    tool.toInherit();
                }
            });
            tools.addAll(inheritTools.values());
        }
    }

    private void checkFirstMessage(List<Message> inputMessages, String previousThreadId, String previousRunId) {
        if(inputMessages.isEmpty() || previousThreadId == null || previousRunId == null) {
            return;
        }
        // 检查第一条消息是否合法
        List<RunStep> runSteps = runService.getRunSteps(previousThreadId, previousRunId);
        if(CollectionUtils.isEmpty(runSteps)) {
            return;
        }
        Message preMessage = null;
        boolean needAdded = false;
        Set<String> approveIds = new HashSet<>();
        for (RunStep runStep : runSteps) {
            RunStatus runStatus = RunStatus.fromValue(runStep.getStatus());
            if(runStep.getType().equals("tool_calls")) {
                if(runStatus == RunStatus.REQUIRES_ACTION) {
                    //查找需要提交工具结果的tool_call
                    List<com.theokanning.openai.assistants.run.ToolCall> toolCalls = runStep.getStepDetails().getToolCalls().stream()
                            .filter(toolCall -> toolCall.getFunction() != null && toolCall.getFunction().getOutput() == null)
                            .toList();
                    if(!toolCalls.isEmpty()) {
                        MessageDb toolCallDb = MessageUtils.convertToolCallMessageFromStepDetails(null, runStep.getStepDetails());
                        preMessage = MessageUtils.convertToInfo(toolCallDb);
                        needAdded = true;
                        if(runStep.getStepDetails().getApprovalIds() != null) {
                            approveIds.addAll(runStep.getStepDetails().getApprovalIds());
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
        MessageUtils.checkPre(preMessage, inputMessages.get(0), approveIds);
        if(!approveIds.isEmpty()) {
            throw new BizParamCheckException("approves required with id: " +  JacksonUtils.serialize(approveIds));
        }
        if(needAdded) {
            inputMessages.add(0, preMessage);
        }
    }

    private ResponseIdMappingDb checkAndStore(String responseId, String threadId, String runId, String previousResponseId, String user) {
        if(previousResponseId == null) {
            return store(responseId, threadId,  runId, "", user);
        }
        return threadLockService.executeWithWriteLock(previousResponseId, () -> {
            ResponseIdMappingDb exist = responseIdMappingRepo.findByPreviousResponseId(previousResponseId);
            if(exist != null) {
                // previous already used -> branch by forking up to assistant message and include tool calls
                ResponseIdMappingDb prev = responseIdMappingRepo.findByResponseId(previousResponseId);
                if(prev == null) {
                    throw new BizParamCheckException("previous response not found");
                }
                String newThreadId = threadService.forkThreadBeforeTargetRun(prev.getThreadId(), prev.getRunId()).getId();
                return store(responseId, newThreadId, runId, previousResponseId, user);
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
        Map<String, String> meta = new HashMap<>();
        if(request.getConversation() != null && !request.getConversation().isString()) {
            meta.putAll(request.getConversation().getObjectValue().getMetadata());
        }
        ThreadDb threadDb = new ThreadDb();
        threadDb.setUser(BellaContext.getOwnerCode());
        threadDb.setOwner(BellaContext.getOwnerCode());
        threadDb.setMetadata(JacksonUtils.serialize(meta));

        return threadService.createThread(threadDb, null, new ArrayList<>());
    }

    private void processToolCallSteps(RunStep runStep, Map<String, Tool> toolMap, List<Message> messages) {
        // Extract tool call messages from step details
        if (runStep.getStepDetails() != null &&
                runStep.getStepDetails().getToolCalls() != null) {
            Map<String, String> toolCallItemTypes = new HashMap<>();
            Map<String, String> toolResultItemTypes = new HashMap<>();
            Map<String, String> itemIds = new HashMap<>();
            runStep.getStepDetails().getToolCalls().forEach(
                    toolCall -> {
                        String itemId = runStep.getThreadId().concat("_").concat(runStep.getId()).concat("_").concat(toolCall.getId() == null ? "" : toolCall.getId());
                        Tool tool =  toolMap.getOrDefault(toolCall.getType(), toolMap.get(toolCall.getFunction() == null ? "" : toolCall.getFunction().getName()));
                        if(tool != null && !tool.hidden()) {
                            toolCallItemTypes.put(toolCall.getId() + "_type", ResponseUtils.converterToToolCallItemType(tool));
                            toolResultItemTypes.put(toolCall.getId() + "_type", ResponseUtils.converterToToolOutputItemType(tool));
                            itemIds.put(toolCall.getId() + "_id", itemId);
                        } else if(tool == null) {
                            if(toolCall.getFunction() != null) {
                                String name = toolCall.getFunction().getName();
                                String[] strs = name.split("_", 2);
                                if(strs.length == 2) {
                                    tool = toolMap.get(strs[0]);
                                    if(tool instanceof Tool.MCP) {
                                        if(strs[1].endsWith("_approval")) {
                                            toolCallItemTypes.put(toolCall.getId() + "_type", "mcp_call_approval");
                                            toolResultItemTypes.put(toolCall.getId() + "_type", "mcp_call_approval");
                                            itemIds.put(toolCall.getId() + "_id", itemId);
                                        } else {
                                            toolCallItemTypes.put(toolCall.getId() + "_type", "mcp_call");
                                            itemIds.put(toolCall.getId() + "_id", itemId);
                                        }
                                    }
                                }
                            }
                        }
                    }
            );
            MessageDb toolCallDb = MessageUtils.convertToolCallMessageFromStepDetails(runStep.getThreadId(), runStep.getStepDetails());
            Message toolCallMessage = MessageUtils.convertToInfo(toolCallDb);
            if (toolCallMessage != null) {
                toolCallMessage.getMetadata().putAll(toolCallItemTypes);
                toolCallMessage.getMetadata().putAll(itemIds);
                messages.add(toolCallMessage);
                MessageDb toolResultDb = MessageUtils.convertToToolResult(runStep.getThreadId(), runStep.getStepDetails(), runStep.getLastError());
                if(toolResultDb != null) {
                    Message toolResultMessage = MessageUtils.convertToInfo(toolResultDb);
                    toolResultMessage.getMetadata().putAll(toolResultItemTypes);
                    toolResultMessage.getMetadata().putAll(itemIds);
                    messages.add(toolResultMessage);
                }
            }
        }
    }

}
