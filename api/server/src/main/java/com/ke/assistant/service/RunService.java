package com.ke.assistant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.assistant.common.Tool;
import com.ke.assistant.db.generated.tables.pojos.RunDb;
import com.ke.assistant.db.generated.tables.pojos.RunStepDb;
import com.ke.assistant.db.generated.tables.pojos.RunToolDb;
import com.ke.assistant.db.repo.Page;
import com.ke.assistant.db.repo.RunRepo;
import com.ke.assistant.db.repo.RunStepRepo;
import com.ke.assistant.db.repo.RunToolRepo;
import com.ke.assistant.run.RunInfo;
import com.ke.assistant.util.BeanUtils;
import com.ke.bella.openapi.common.exception.ResourceNotFoundException;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public RunInfo getRunById(String id) {
        RunDb runDb = runRepo.findById(id);
        return runDb != null ? convertToInfo(runDb) : null;
    }

    /**
     * 根据Thread ID查询Run列表
     */
    public List<RunInfo> getRunsByThreadId(String threadId) {
        List<RunDb> runs = runRepo.findByThreadId(threadId);
        return runs.stream().map(this::convertToInfo).collect(java.util.stream.Collectors.toList());
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
    public Page<RunInfo> getRunsByThreadIdWithPage(String threadId, int page, int pageSize) {
        Page<RunDb> dbPage = runRepo.findByThreadIdWithPage(threadId, page, pageSize);
        List<RunInfo> infoList = dbPage.getList().stream().map(this::convertToInfo).collect(java.util.stream.Collectors.toList());
        Page<RunInfo> result = new Page<>();
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
    public RunInfo updateRun(String id, Map<String, Object> metaData) {
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
    public List<RunStepDb> getRunSteps(String runId) {
        return runStepRepo.findByRunId(runId);
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
    private RunInfo convertToInfo(RunDb runDb) {
        if(runDb == null) {
            return null;
        }

        RunInfo info = new RunInfo();
        BeanUtils.copyProperties(runDb, info);

        // 转换metadata从JSON字符串到Map
        if(StringUtils.isNotBlank(runDb.getMetadata())) {
            info.setMetadata(JacksonUtils.toMap(runDb.getMetadata()));
        }

        // 反序列化复杂字段
        if(StringUtils.isNotBlank(runDb.getRequiredAction())) {
            info.setRequiredAction(JacksonUtils.deserialize(runDb.getRequiredAction(), RunInfo.RequiredAction.class));
        }

        if(StringUtils.isNotBlank(runDb.getLastError())) {
            info.setLastError(JacksonUtils.deserialize(runDb.getLastError(), RunInfo.LastError.class));
        }

        if(StringUtils.isNotBlank(runDb.getUsage())) {
            info.setUsage(JacksonUtils.deserialize(runDb.getUsage(), new TypeReference<RunInfo.Usage>() {
            }));
        }

        if(StringUtils.isNotBlank(runDb.getTruncationStrategy())) {
            info.setTruncationStrategy(JacksonUtils.deserialize(runDb.getTruncationStrategy(), new TypeReference<Object>() {
            }));
        }

        if(StringUtils.isNotBlank(runDb.getToolChoice())) {
            info.setToolChoice(JacksonUtils.deserialize(runDb.getToolChoice(), new TypeReference<Object>() {
            }));
        }

        if(StringUtils.isNotBlank(runDb.getResponseFormat())) {
            Object responseFormat = JacksonUtils.deserialize(runDb.getResponseFormat(), new TypeReference<Object>() {
            });
            info.setResponseFormat(responseFormat != null ? responseFormat : runDb.getResponseFormat());
        }

        if(StringUtils.isNotBlank(runDb.getIncompleteDetails())) {
            Object incompleteDetails = JacksonUtils.deserialize(runDb.getIncompleteDetails(), new TypeReference<Object>() {
            });
            info.setIncompleteDetails(incompleteDetails != null ? incompleteDetails : runDb.getIncompleteDetails());
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
