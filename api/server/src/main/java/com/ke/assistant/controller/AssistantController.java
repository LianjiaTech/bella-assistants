package com.ke.assistant.controller;

import com.ke.assistant.assistant.AssistantInfo;
import com.ke.assistant.assistant.AssistantOps;
import com.ke.assistant.common.CommonPage;
import com.ke.assistant.common.DeleteResponse;
import com.ke.assistant.db.generated.tables.pojos.AssistantDb;
import com.ke.assistant.db.repo.Page;
import com.ke.assistant.service.AssistantService;
import com.ke.assistant.util.BeanUtils;
import com.ke.assistant.util.ToolResourceUtils;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.common.exception.ResourceNotFoundException;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
@Slf4j
public class AssistantController {

    private final AssistantService assistantService;

    /**
     * 创建 Assistant
     */
    @PostMapping
    public AssistantInfo createAssistant(
            @RequestBody AssistantOps.CreateAssistantOp request) {

        // transfer请求到数据库对象
        AssistantDb assistant = new AssistantDb();
        BeanUtils.copyProperties(request, assistant);
        assistant.setOwner(BellaContext.getOwnerCode());

        // 将metadata Map转换为JSON字符串
        if(request.getMetadata() != null) {
            assistant.setMetadata(JacksonUtils.serialize(request.getMetadata()));
        }

        // 处理 tool_resources
        List<Map<String, String>> toolResourceFiles = request.getToolResources() != null ?
                ToolResourceUtils.toolResourceToFiles(request.getToolResources()) : null;

        return assistantService.createAssistant(assistant, request.getFileIds(), request.getTools(), toolResourceFiles);
    }

    /**
     * 获取 Assistant 详情
     */
    @GetMapping("/{assistant_id}")
    public AssistantInfo getAssistant(
            @PathVariable("assistant_id") String assistantId) {

        AssistantInfo info = assistantService.getAssistantById(assistantId);
        if(info == null) {
            throw new ResourceNotFoundException("Assistant not found");
        }
        return info;
    }

    /**
     * 获取 Assistant 列表
     */
    @GetMapping
    public CommonPage<AssistantInfo> listAssistants(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {

        String owner = BellaContext.getOwnerCode();

        Page<AssistantInfo> infoPage = assistantService.getAssistantsByOwnerWithPage(owner, page, pageSize);

        List<AssistantInfo> infoList = infoPage.getList();

        String firstId = infoList.isEmpty() ? null : infoList.get(0).getId();
        String lastId = infoList.isEmpty() ? null : infoList.get(infoList.size() - 1).getId();
        boolean hasMore = (long) infoPage.getPage() * infoPage.getPageSize() < infoPage.getTotal();

        return new CommonPage<>(infoList, firstId, lastId, hasMore);
    }

    /**
     * 更新 Assistant
     */
    @PostMapping("/{assistant_id}")
    public AssistantInfo updateAssistant(
            @PathVariable("assistant_id") String assistantId,
            @RequestBody AssistantOps.UpdateAssistantOp request) {

        request.setOwner(BellaContext.getOwnerCode());

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

        return assistantService.updateAssistant(assistantId, updateData, request.getFileIds(), request.getTools(), toolResourceFiles,
                request.getOwner());
    }

    /**
     * 删除 Assistant
     */
    @DeleteMapping("/{assistant_id}")
    public DeleteResponse deleteAssistant(
            @PathVariable("assistant_id") String assistantId) {
        String owner = BellaContext.getOwnerCode();
        boolean deleted = assistantService.deleteAssistant(assistantId, owner);
        return new DeleteResponse(assistantId, "assistant", deleted);
    }
}
