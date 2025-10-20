package com.ke.assistant.service;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.assistant.db.generated.tables.pojos.AssistantDb;
import com.ke.assistant.db.generated.tables.pojos.AssistantFileRelationDb;
import com.ke.assistant.db.generated.tables.pojos.AssistantToolDb;
import com.ke.assistant.db.repo.AssistantFileRelationRepo;
import com.ke.assistant.db.repo.AssistantRepo;
import com.ke.assistant.db.repo.AssistantToolRepo;
import com.ke.assistant.util.BeanUtils;
import com.ke.assistant.util.ToolResourceUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.assistants.assistant.Assistant;
import com.theokanning.openai.assistants.assistant.Tool;
import com.theokanning.openai.assistants.assistant.ToolResources;

import lombok.extern.slf4j.Slf4j;

/**
 * Assistant Service
 */
@Service
@Slf4j
public class AssistantService {

    @Autowired
    private AssistantRepo assistantRepo;
    @Autowired
    private AssistantFileRelationRepo assistantFileRepo;
    @Autowired
    private AssistantToolRepo assistantToolRepo;

    /**
     * 创建 Assistant
     */
    @Transactional
    public Assistant createAssistant(AssistantDb assistant, List<String> fileIds, List<Tool> tools,
            List<Map<String, String>> toolResourceFiles) {
        // 设置默认值
        assistant.setObject("assistant");
        if(StringUtils.isBlank(assistant.getMetadata())) {
            assistant.setMetadata("{}");
        }

        // 插入 Assistant
        AssistantDb savedAssistant = assistantRepo.insert(assistant);

        // 处理文件关联和工具配置
        updateAssistantFilesAndTools(savedAssistant.getId(), fileIds, tools, toolResourceFiles);

        return convertToInfo(savedAssistant);
    }

    /**
     * 根据ID获取Assistant
     */
    public Assistant getAssistantById(String id) {
        AssistantDb assistantDb = assistantRepo.findById(id);
        return assistantDb != null ? convertToInfo(assistantDb) : null;
    }

    /**
     * 检查Assistant所有权
     */
    public boolean checkOwnership(String id, String owner) {
        return assistantRepo.checkOwnership(id, owner);
    }

    /**
     * 根据owner查询Assistant列表
     */
    public List<Assistant> getAssistantsByOwner(String owner) {
        List<AssistantDb> assistants = assistantRepo.findByOwner(owner);
        return assistants.stream().map(this::convertToInfo).collect(Collectors.toList());
    }


    /**
     * 基于游标的分页查询Assistant
     */
    public List<Assistant> getAssistantsByCursor(String owner, String after, String before, int limit, String order) {
        List<AssistantDb> assistants = assistantRepo.findByOwnerWithCursor(owner, after, before, limit, order);
        return assistants.stream().map(this::convertToInfo).collect(Collectors.toList());
    }

    /**
     * 更新Assistant
     */
    @Transactional
    public Assistant updateAssistant(String id, AssistantDb updateData, List<String> fileIds, List<Tool> tools, List<Map<String, String>> toolResourceFiles) {
        AssistantDb existing = assistantRepo.findById(id);
        if(existing == null) {
            throw new IllegalArgumentException("Assistant not found: " + id);
        }

        BeanUtils.copyNonNullProperties(updateData, existing);

        existing.setObject("assistant");
        if(StringUtils.isBlank(existing.getMetadata())) {
            existing.setMetadata("{}");
        }

        assistantRepo.update(existing);

        // 更新文件关联和工具配置 - 和创建逻辑一致
        updateAssistantFilesAndTools(id, fileIds, tools, toolResourceFiles);

        return convertToInfo(existing);
    }

    /**
     * 删除Assistant
     */
    @Transactional
    public boolean deleteAssistant(String id, String owner) {
        // 权限检查在Service中进行
        if(!checkOwnership(id, owner)) {
            throw new IllegalArgumentException("Permission denied");
        }

        // 删除关联的文件
        assistantFileRepo.deleteByAssistantId(id);
        // 删除关联的工具
        assistantToolRepo.deleteByAssistantId(id);
        // 删除Assistant本身
        return assistantRepo.deleteById(id);
    }

    /**
     * 检查Assistant是否存在
     */
    public boolean existsById(String id) {
        return assistantRepo.existsById(id);
    }

    /**
     * 获取Assistant的文件列表
     */
    public List<AssistantFileRelationDb> getAssistantFiles(String assistantId) {
        return assistantFileRepo.findByAssistantId(assistantId);
    }

    /**
     * 获取Assistant的工具列表
     */
    public List<AssistantToolDb> getAssistantTools(String assistantId) {
        return assistantToolRepo.findByAssistantId(assistantId);
    }

    /**
     * 更新Assistant的文件关联和工具配置 统一处理创建和更新时的文件和工具逻辑
     */
    @Transactional
    public void updateAssistantFilesAndTools(String assistantId, List<String> fileIds, List<Tool> tools,
            List<Map<String, String>> toolResourceFiles) {

        // 处理普通文件关联（file_ids参数，tool_name="_all"）
        if(fileIds != null) {
            // 删除现有的文件关联
            assistantFileRepo.deleteByAssistantIdWithAllTools(assistantId);
            for (String fileId : fileIds) {
                AssistantFileRelationDb assistantFile = new AssistantFileRelationDb();
                assistantFile.setFileId(fileId);
                assistantFile.setAssistantId(assistantId);
                assistantFile.setObject("assistant.file");
                assistantFile.setToolName("_all");
                assistantFileRepo.insert(assistantFile);
            }
        }

        // 处理工具资源文件（tool_resources参数，不同的tool_name）
        // 重要：只处理那些不在 file_ids 中的文件
        if(toolResourceFiles != null) {
            // 删除现有的文件关联
            assistantFileRepo.deleteByAssistantIdWithToolResources(assistantId);
            for (Map<String, String> fileRequest : toolResourceFiles) {
                String fileId = fileRequest.get("file_id");
                String toolName = fileRequest.get("tool_name");
                // 过滤掉已经在 file_ids 中的文件
                if(fileIds == null || !fileIds.contains(fileId)) {
                    AssistantFileRelationDb assistantFile = new AssistantFileRelationDb();
                    assistantFile.setFileId(fileId);
                    assistantFile.setAssistantId(assistantId);
                    assistantFile.setObject("assistant.file");
                    assistantFile.setToolName(toolName);
                    assistantFileRepo.insert(assistantFile);
                }
            }
        }

        // 处理工具配置
        if(tools != null) {
            // 删除现有的工具配置
            assistantToolRepo.deleteByAssistantId(assistantId);
            for (Tool tool : tools) {
                AssistantToolDb assistantTool = new AssistantToolDb();
                assistantTool.setAssistantId(assistantId);
                assistantTool.setTool(JacksonUtils.serialize(tool));
                assistantToolRepo.insert(assistantTool);
            }
        }
    }

    /**
     * 将AssistantDb转换为AssistantInfo
     */
    @SuppressWarnings("unchecked")
    private Assistant convertToInfo(AssistantDb assistantDb) {
        if(assistantDb == null) {
            return null;
        }

        Assistant info = new Assistant();
        // 进行基础字段拷贝
        BeanUtils.copyProperties(assistantDb, info);

        info.setCreatedAt((int) assistantDb.getCreatedAt().toEpochSecond(ZoneOffset.ofHours(8)));

        // 转换metadata从JSON字符串到Map
        if(StringUtils.isNotBlank(assistantDb.getMetadata())) {
            info.setMetadata(JacksonUtils.deserialize(assistantDb.getMetadata(), new TypeReference<>() {}));
        }

        // 设置关联数据
        List<AssistantFileRelationDb> files = getAssistantFiles(assistantDb.getId());
        List<AssistantToolDb> tools = getAssistantTools(assistantDb.getId());

        // 计算file_ids
        List<String> fileIds = new ArrayList<>();
        for (AssistantFileRelationDb file : files) {
            if("_all".equals(file.getToolName())) {
                fileIds.add(file.getFileId());
            }
        }
        info.setFileIds(fileIds);

        // 计算tool_resources - 使用工具类构建正确的嵌套结构
        List<Map<String, String>> toolFiles = new ArrayList<>();
        for (AssistantFileRelationDb file : files) {
            if(!"_all".equals(file.getToolName())) {
                Map<String, String> fileMap = new HashMap<>();
                fileMap.put("file_id", file.getFileId());
                fileMap.put("tool_name", file.getToolName());
                toolFiles.add(fileMap);
            }
        }
        ToolResources toolResources = ToolResourceUtils.buildToolResourcesFromFiles(toolFiles);
        info.setToolResources(toolResources);

        // 计算tools
        List<Tool> toolList = new ArrayList<>();
        for (AssistantToolDb toolDb : tools) {
            if(StringUtils.isNotBlank(toolDb.getTool())) {
                Tool tool = JacksonUtils.deserialize(toolDb.getTool(), Tool.class);
                if(tool != null) {
                    toolList.add(tool);
                }
            }
        }
        info.setTools(toolList);

        return info;
    }
}
