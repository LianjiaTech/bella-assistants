package com.ke.assistant.service;

import com.ke.assistant.db.generated.tables.pojos.RunDb;
import com.ke.assistant.db.generated.tables.pojos.RunStepDb;
import com.ke.assistant.db.generated.tables.pojos.RunToolDb;
import com.ke.assistant.db.repo.RunRepo;
import com.ke.assistant.db.repo.RunStepRepo;
import com.ke.assistant.db.repo.RunToolRepo;
import com.ke.assistant.util.BeanUtils;
import com.ke.assistant.util.RunUtils;
import com.ke.assistant.util.ToolResourceUtils;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.common.exception.ResourceNotFoundException;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.Usage;
import com.theokanning.openai.assistants.assistant.Assistant;
import com.theokanning.openai.assistants.assistant.Tool;
import com.theokanning.openai.assistants.message.IncompleteDetails;
import com.theokanning.openai.assistants.message.Message;
import com.theokanning.openai.assistants.message.MessageRequest;
import com.theokanning.openai.assistants.run.MessageCreation;
import com.theokanning.openai.assistants.run.RequiredAction;
import com.theokanning.openai.assistants.run.Run;
import com.theokanning.openai.assistants.run.RunCreateRequest;
import com.theokanning.openai.assistants.run.ToolChoice;
import com.theokanning.openai.assistants.run.ToolFiles;
import com.theokanning.openai.assistants.run.TruncationStrategy;
import com.theokanning.openai.assistants.run_step.RunStep;
import com.theokanning.openai.assistants.run_step.StepDetails;
import com.theokanning.openai.common.LastError;
import com.theokanning.openai.completion.chat.ChatResponseFormat;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Run Service
 */
@Service
@Slf4j
public class RunService {

    @Autowired
    private RunRepo runRepo;
    @Autowired
    private RunStepRepo runStepRepo;
    @Autowired
    private RunToolRepo runToolRepo;
    @Autowired
    private AssistantService assistantService;
    @Autowired
    @Lazy
    private MessageService messageService;

    /**
     * 根据ID获取Run
     */
    public Run getRunById(String threadId, String id) {
        RunDb runDb = runRepo.findById(threadId, id);
        return runDb != null ? convertToInfo(runDb) : null;
    }

    /**
     * 根据Thread ID查询Run列表
     */
    public List<Run> getRunsByThreadId(String threadId) {
        List<RunDb> runs = runRepo.findByThreadId(threadId);
        return runs.stream().map(this::convertToInfo).collect(Collectors.toList());
    }


    /**
     * 基于游标的分页查询Thread下的Run
     */
    public List<Run> getRunsByCursor(String threadId, String after, String before, int limit, String order) {
        List<RunDb> runs = runRepo.findByThreadIdWithCursor(threadId, after, before, limit, order);
        return runs.stream().map(this::convertToInfo).collect(Collectors.toList());
    }

    /**
     * 更新Run
     */
    @Transactional
    public Run updateRun(String threadId, String id, Map<String, String> metaData) {
        RunDb existing = runRepo.findById(threadId, id);
        if(existing == null) {
            throw new ResourceNotFoundException("Run not found: " + id);
        }

        if(metaData == null) {
            metaData = new HashMap<>();
        }

        existing.setMetadata(JacksonUtils.serialize(metaData));

        runRepo.update(existing);
        return convertToInfo(existing);
    }

    /**
     * 获取Run的Steps列表
     */
    public List<RunStep> getRunSteps(String threadId, String runId) {
        List<RunStepDb> runSteps = runStepRepo.findByRunId(threadId, runId);
        return runSteps.stream().map(RunUtils::convertStepToInfo).collect(Collectors.toList());
    }

    /**
     * 获取Thread的Steps列表
     */
    public List<RunStep> getThreadSteps(String threadId) {
        List<RunStepDb> runSteps = runStepRepo.findByThreadId(threadId);
        return runSteps.stream().map(RunUtils::convertStepToInfo).collect(Collectors.toList());
    }

    /**
     * 获取Run的Tools列表
     */
    public List<RunToolDb> getRunTools(String runId) {
        return runToolRepo.findByRunId(runId);
    }

    /**
     * 将RunDb转换为RunInfo
     */
    @SuppressWarnings("unchecked")
    private Run convertToInfo(RunDb runDb) {
        if(runDb == null) {
            return null;
        }

        Run info = new Run();
        BeanUtils.copyProperties(runDb, info);

        info.setCreateTime(runDb.getCreatedAt());

        info.setCreatedAt((int) runDb.getCreatedAt().toEpochSecond(ZoneOffset.ofHours(8)));
        if(runDb.getStartedAt() != null) {
            info.setStartedAt((int) runDb.getStartedAt().toEpochSecond(ZoneOffset.ofHours(8)));
        }
        if(runDb.getCancelledAt() != null) {
            info.setCancelledAt((int) runDb.getCancelledAt().toEpochSecond(ZoneOffset.ofHours(8)));
        }
        if(runDb.getExpiresAt() != null) {
            info.setExpiresAt((int) runDb.getExpiresAt().toEpochSecond(ZoneOffset.ofHours(8)));
        }

        // 转换metadata从JSON字符串到Map
        if(StringUtils.isNotBlank(runDb.getMetadata())) {
            info.setMetadata(JacksonUtils.toMap(runDb.getMetadata()));
        }

        // 反序列化复杂字段
        if(StringUtils.isNotBlank(runDb.getRequiredAction())) {
            info.setRequiredAction(JacksonUtils.deserialize(runDb.getRequiredAction(), RequiredAction.class));
        }

        if(StringUtils.isNotBlank(runDb.getLastError())) {
            info.setLastError(JacksonUtils.deserialize(runDb.getLastError(), LastError.class));
        }

        if(StringUtils.isNotBlank(runDb.getUsage())) {
            info.setUsage(JacksonUtils.deserialize(runDb.getUsage(), Usage.class));
        }

        if(StringUtils.isNotBlank(runDb.getTruncationStrategy())) {
            info.setTruncationStrategy(JacksonUtils.deserialize(runDb.getTruncationStrategy(), TruncationStrategy.class));
        }

        if(StringUtils.isNotBlank(runDb.getToolChoice())) {
            switch (runDb.getToolChoice()) {
            case "auto":
                info.setToolChoice(ToolChoice.AUTO);
                break;
            case "none":
                info.setToolChoice(ToolChoice.NONE);
                break;
            case "required":
                info.setToolChoice(ToolChoice.REQUIRED);
                break;
            default:
                info.setToolChoice(JacksonUtils.deserialize(runDb.getToolChoice(), ToolChoice.class));
            }
        }

        if(StringUtils.isNotBlank(runDb.getResponseFormat())) {
            switch (runDb.getResponseFormat()) {
            case "text":
                info.setResponseFormat(ChatResponseFormat.TEXT);
                break;
            case "json_object":
                info.setResponseFormat(ChatResponseFormat.JSON_OBJECT);
                break;
            case "json_schema":
                info.setResponseFormat(JacksonUtils.deserialize(runDb.getResponseFormat(), ChatResponseFormat.class));
                break;
            default:
                info.setResponseFormat(ChatResponseFormat.AUTO);
            }

        }

        if(StringUtils.isNotBlank(runDb.getIncompleteDetails())) {
            info.setIncompleteDetails(JacksonUtils.deserialize(runDb.getIncompleteDetails(), IncompleteDetails.class));
        }

        if(StringUtils.isNotBlank(runDb.getFileIds())) {
            info.setFileIds(JacksonUtils.deserialize(runDb.getFileIds(), ToolFiles.class));
        }

        // 查询关联的RunTool数据获取tools
        List<RunToolDb> runTools = getRunTools(runDb.getId());
        if(!runTools.isEmpty()) {
            List<Tool> tools = new ArrayList<>();
            for (RunToolDb runTool : runTools) {
                if(StringUtils.isNotBlank(runTool.getTool())) {
                    Tool tool = JacksonUtils.deserialize(runTool.getTool(), Tool.class);
                    if(tool != null) {
                        tools.add(tool);
                    }
                }
            }
            info.setTools(tools);
        }

        return info;
    }

    /**
     * 创建Run
     */
    @Transactional
    public Pair<Run, String> createRun(String threadId, RunCreateRequest request) {

        Assistant assistant = assistantService.getAssistantById(request.getAssistantId());

        // 验证assistant是否存在
        if(assistant == null) {
            throw new ResourceNotFoundException("Assistant not found: " + request.getAssistantId());
        }
        
        // 创建run记录
        RunDb runDb = new RunDb();
        BeanUtils.copyProperties(request, runDb);
        runDb.setThreadId(threadId);
        runDb.setUser(BellaContext.getOwnerCode());
        if(runDb.getModel() == null) {
            runDb.setModel(assistant.getModel());
        }
        if(runDb.getModel() == null) {
            throw new BizParamCheckException("model is null");
        }
        if(runDb.getInstructions() == null) {
            runDb.setInstructions(assistant.getInstructions());
        }
        if(runDb.getReasoningEffort() == null) {
            runDb.setReasoningEffort(assistant.getReasoningEffort());
        }
        if(request.getAdditionalInstructions() != null) {
            if(runDb.getInstructions() != null) {
                runDb.setInstructions(runDb.getInstructions() + request.getAdditionalInstructions());
            } else {
                runDb.setInstructions(request.getAdditionalInstructions());
            }
        }
        runDb.setStatus("queued");

        if(runDb.getTemperature() == null) {
            runDb.setTemperature(assistant.getTemperature());
        }

        if(runDb.getTopP() == null) {
            runDb.setTopP(assistant.getTopP());
        }

        if(request.getResponseFormat() != null) {
            runDb.setResponseFormat(JacksonUtils.serialize(request.getResponseFormat()));
        } else if(assistant.getResponseFormat() != null) {
            runDb.setResponseFormat(JacksonUtils.serialize(assistant.getResponseFormat()));
        }
        
        // 设置其他参数
        if(request.getMetadata() != null) {
            runDb.setMetadata(JacksonUtils.serialize(request.getMetadata()));
        }
        if(request.getTruncationStrategy() != null) {
            runDb.setTruncationStrategy(JacksonUtils.serialize(request.getTruncationStrategy()));
        }
        if(request.getToolChoice() != null) {
            runDb.setToolChoice(JacksonUtils.serialize(request.getToolChoice()));
        }

        // 保存file
        Map<String, List<String>> toolFilesMap = new HashMap<>();

        if(assistant.getToolResources() != null) {
            toolFilesMap = ToolResourceUtils.toolResourcesToToolFiles(assistant.getToolResources());
        }

        if(assistant.getFileIds() != null) {
            toolFilesMap.put("_all", assistant.getFileIds());
        }

        if(!toolFilesMap.isEmpty()) {
            ToolFiles toolFiles = new ToolFiles();
            toolFiles.setTools(toolFilesMap);
            runDb.setFileIds(JacksonUtils.serialize(toolFiles));
        }
        
        // 保存run
        runDb = runRepo.insert(runDb);
        
        // 保存tools
        if(request.getTools() != null && !request.getTools().isEmpty()) {
            createRunTool(request.getTools(), runDb.getId());
        } else if(assistant.getTools() != null && !assistant.getTools().isEmpty()) {
            createRunTool(assistant.getTools(), runDb.getId());
        }

        // 处理additional_messages
        if(request.getAdditionalMessages() != null && !request.getAdditionalMessages().isEmpty()) {
            for(MessageRequest additionalMsg : request.getAdditionalMessages()) {
                messageService.createMessage(threadId, additionalMsg, "completed", Boolean.FALSE == request.getSaveMessage());
            }
        }
        
        // 创建初始的assistant消息
        MessageRequest messageRequest = MessageRequest.builder()
                .role("assistant")
                .textMessage("")
                .build();
        
        // 使用MessageService创建消息
        Message assistantMessage = messageService.createRunStepMessage(threadId, messageRequest);

        RunStepDb runStep = new RunStepDb();
        runStep.setRunId(runDb.getId());
        runStep.setThreadId(threadId);
        runStep.setAssistantId(assistant.getId());
        runStep.setType("message_creation");
        runStep.setStatus("in_progress");
        runStep.setCreatedAt(LocalDateTime.now());

        StepDetails stepDetails = StepDetails.builder()
                .type("message_creation")
                .messageCreation(new MessageCreation(assistantMessage.getId()))
                .build();
        runStep.setStepDetails(JacksonUtils.serialize(stepDetails));

        runStepRepo.insert(runStep);
        
        return Pair.of(convertToInfo(runDb), assistantMessage.getId());
    }


    private void createRunTool(List<Tool> tools, String runId) {
        if(tools == null) {
            return;
        }
        for(Tool tool : tools) {
            RunToolDb runTool = new RunToolDb();
            runTool.setRunId(runId);
            runTool.setTool(JacksonUtils.serialize(tool));
            runToolRepo.insert(runTool);
        }
    }

}
