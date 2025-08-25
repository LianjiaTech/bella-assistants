package com.ke.assistant.service;

import com.ke.assistant.db.generated.tables.pojos.RunDb;
import com.ke.assistant.db.generated.tables.pojos.RunStepDb;
import com.ke.assistant.db.generated.tables.pojos.RunToolDb;
import com.ke.assistant.db.repo.Page;
import com.ke.assistant.db.repo.RunRepo;
import com.ke.assistant.db.repo.RunStepRepo;
import com.ke.assistant.db.repo.RunToolRepo;
import com.ke.assistant.util.BeanUtils;
import com.ke.assistant.util.RunUtils;
import com.ke.bella.openapi.common.exception.ResourceNotFoundException;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.Usage;
import com.theokanning.openai.assistants.assistant.Tool;
import com.theokanning.openai.assistants.message.IncompleteDetails;
import com.theokanning.openai.assistants.run.RequiredAction;
import com.theokanning.openai.assistants.run.Run;
import com.theokanning.openai.assistants.run.ToolChoice;
import com.theokanning.openai.assistants.run.ToolFiles;
import com.theokanning.openai.assistants.run.TruncationStrategy;
import com.theokanning.openai.assistants.run_step.RunStep;
import com.theokanning.openai.assistants.run_step.StepDetails;
import com.theokanning.openai.common.LastError;
import com.theokanning.openai.completion.chat.ChatResponseFormat;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 根据ID获取Run
     */
    public Run getRunById(String id) {
        RunDb runDb = runRepo.findById(id);
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
     * 根据Assistant ID查询Run列表
     */
    public List<RunDb> getRunsByAssistantId(String assistantId) {
        return runRepo.findByAssistantId(assistantId);
    }

    /**
     * 根据状态查询Run列表
     */
    public List<RunDb> getRunsByStatus(String status) {
        return runRepo.findByStatus(status);
    }

    /**
     * 分页查询Thread下的Run
     */
    public Page<Run> getRunsByThreadIdWithPage(String threadId, int page, int pageSize) {
        Page<RunDb> dbPage = runRepo.findByThreadIdWithPage(threadId, page, pageSize);
        List<Run> infoList = dbPage.getList().stream().map(this::convertToInfo).collect(Collectors.toList());
        Page<Run> result = new Page<>();
        result.setPage(dbPage.getPage());
        result.setPageSize(dbPage.getPageSize());
        result.setTotal(dbPage.getTotal());
        result.setList(infoList);
        return result;
    }

    /**
     * 更新Run
     */
    @Transactional
    public Run updateRun(String id, Map<String, String> metaData) {
        RunDb existing = runRepo.findById(id);
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
     * 更新Run状态
     */
    @Transactional
    public boolean updateRunStatus(String id, String status) {
        return runRepo.updateStatus(id, status);
    }

    /**
     * 根据Task ID查询Run
     */
    public RunDb getRunByTaskId(String taskId) {
        return runRepo.findByTaskId(taskId);
    }

    /**
     * 检查Run是否存在
     */
    public boolean existsById(String id) {
        return runRepo.existsById(id);
    }

    /**
     * 获取Run的Steps列表
     */
    public List<RunStep> getRunSteps(String runId) {
        List<RunStepDb> runSteps = runStepRepo.findByRunId(runId);
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
            info.setToolChoice(JacksonUtils.deserialize(runDb.getToolChoice(), ToolChoice.class));
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

}
