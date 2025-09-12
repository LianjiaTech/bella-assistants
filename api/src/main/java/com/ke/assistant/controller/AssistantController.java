package com.ke.assistant.controller;

import com.ke.assistant.db.generated.tables.pojos.AssistantDb;
import com.ke.assistant.model.CommonPage;
import com.ke.assistant.model.DeleteResponse;
import com.ke.assistant.service.AssistantService;
import com.ke.assistant.util.BeanUtils;
import com.ke.assistant.util.ToolResourceUtils;
import com.ke.assistant.util.ToolUtils;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.common.exception.ResourceNotFoundException;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.assistants.assistant.Assistant;
import com.theokanning.openai.assistants.assistant.AssistantRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Assistant Controller
 */
@RestController
@RequestMapping("/v1/assistants")
@Slf4j
public class AssistantController {

    @Autowired
    private AssistantService assistantService;

    /**
     * 创建 Assistant
     */
    @PostMapping
    public Assistant createAssistant(
            @RequestBody AssistantRequest request) {

        // transfer请求到数据库对象
        AssistantDb assistant = new AssistantDb();
        BeanUtils.copyProperties(request, assistant);
        assistant.setOwner(BellaContext.getOwnerCode());
        assistant.setUser(BellaContext.getOwnerCode());

        // 将metadata Map转换为JSON字符串
        if(request.getMetadata() != null) {
            assistant.setMetadata(JacksonUtils.serialize(request.getMetadata()));
        }

        // 处理 tool_resources
        List<Map<String, String>> toolResourceFiles = request.getToolResources() != null ?
                ToolResourceUtils.toolResourceToFiles(request.getToolResources()) : null;

        ToolUtils.checkTools(request.getTools());

        return assistantService.createAssistant(assistant, request.getFileIds(), request.getTools(), toolResourceFiles);
    }

    /**
     * 获取 Assistant 详情
     */
    @GetMapping("/{assistant_id}")
    public Assistant getAssistant(
            @PathVariable("assistant_id") String assistantId) {

        Assistant info = assistantService.getAssistantById(assistantId);
        if(info == null) {
            throw new ResourceNotFoundException("Assistant not found");
        }
        return info;
    }

    /**
     * 获取 Assistant 列表
     */
    @GetMapping
    public CommonPage<Assistant> listAssistants(
            @RequestParam(value = "after", required = false) String after,
            @RequestParam(value = "before", required = false) String before,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "order", defaultValue = "desc") String order) {

        String owner = BellaContext.getOwnerCode();

        List<Assistant> infoList = assistantService.getAssistantsByCursor(owner, after, before, limit + 1, order);

        boolean hasMore = infoList.size() > limit;
        if (hasMore) {
            infoList.remove(infoList.size() - 1);
        }

        String firstId = infoList.isEmpty() ? null : infoList.get(0).getId();
        String lastId = infoList.isEmpty() ? null : infoList.get(infoList.size() - 1).getId();

        return new CommonPage<>(infoList, firstId, lastId, hasMore);
    }

    /**
     * 更新 Assistant
     */
    @PostMapping("/{assistant_id}")
    public Assistant updateAssistant(
            @PathVariable("assistant_id") String assistantId,
            @RequestBody AssistantRequest request) {

        // transfer请求到数据库对象
        AssistantDb updateData = new AssistantDb();
        BeanUtils.copyProperties(request, updateData);

        // 将metadata Map转换为JSON字符串
        if(request.getMetadata() != null) {
            updateData.setMetadata(JacksonUtils.serialize(request.getMetadata()));
        }

        // 处理 tool_resources
        List<Map<String, String>> toolResourceFiles = request.getToolResources() != null ?
                ToolResourceUtils.toolResourceToFiles(request.getToolResources()) : null;

        ToolUtils.checkTools(request.getTools());

        return assistantService.updateAssistant(assistantId, updateData, request.getFileIds(), request.getTools(), toolResourceFiles);
    }

    /**
     * 删除 Assistant
     */
    @DeleteMapping("/{assistant_id}")
    public DeleteResponse deleteAssistant(
            @PathVariable("assistant_id") String assistantId) {
        throw new NotImplementedException("not implemented temporarily");
    }
}
