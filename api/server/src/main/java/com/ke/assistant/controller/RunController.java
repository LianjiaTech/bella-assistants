package com.ke.assistant.controller;

import com.ke.assistant.db.repo.Page;
import com.ke.assistant.model.CommonPage;
import com.ke.assistant.service.RunService;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.common.exception.ResourceNotFoundException;
import com.theokanning.openai.assistants.run.ModifyRunRequest;
import com.theokanning.openai.assistants.run.Run;
import com.theokanning.openai.assistants.run_step.RunStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Run Controller
 */
@RestController
@RequestMapping("/v1/threads/{thread_id}/runs")
@Slf4j
public class RunController {

    @Autowired
    private RunService runService;

    /**
     * 获取 Run 详情
     */
    @GetMapping("/{run_id}")
    public Run getRun(
            @PathVariable("thread_id") String threadId,
            @PathVariable("run_id") String runId) {

        Run run = runService.getRunById(runId);
        if(run == null) {
            throw new ResourceNotFoundException("Run not found");
        }

        // 验证run是否属于指定的thread
        if(!threadId.equals(run.getThreadId())) {
            throw new BizParamCheckException("Run does not belong to this thread");
        }

        return run;
    }

    /**
     * 获取 Thread 的 Run 列表
     */
    @GetMapping
    public CommonPage<Run> listRuns(
            @PathVariable("thread_id") String threadId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {

        Page<Run> infoPage = runService.getRunsByThreadIdWithPage(threadId, page, pageSize);

        List<Run> infoList = infoPage.getList();

        String firstId = infoList.isEmpty() ? null : infoList.get(0).getId();
        String lastId = infoList.isEmpty() ? null : infoList.get(infoList.size() - 1).getId();
        boolean hasMore = (long) infoPage.getPage() * infoPage.getPageSize() < infoPage.getTotal();

        return new CommonPage<>(infoList, firstId, lastId, hasMore);
    }

    /**
     * 更新 Run
     */
    @PostMapping("/{run_id}")
    public Run updateRun(
            @PathVariable("thread_id") String threadId,
            @PathVariable("run_id") String runId,
            @RequestBody ModifyRunRequest request) {

        // 验证run是否存在且属于指定的thread
        Run existing = runService.getRunById(runId);
        if(existing == null) {
            throw new ResourceNotFoundException("Run not found");
        }
        if(!threadId.equals(existing.getThreadId())) {
            throw new BizParamCheckException("Run does not belong to this thread");
        }

        return runService.updateRun(runId, request.getMetadata());
    }

    /**
     * 获取 Run 的 Steps 列表
     */
    @GetMapping("/{run_id}/steps")
    public List<RunStep> getRunSteps(
            @PathVariable("thread_id") String threadId,
            @PathVariable("run_id") String runId) {

        // 验证run是否存在且属于指定的thread
        Run existing = runService.getRunById(runId);
        if(existing == null) {
            throw new ResourceNotFoundException("Run not found");
        }
        if(!threadId.equals(existing.getThreadId())) {
            throw new BizParamCheckException("Run does not belong to this thread");
        }

        return runService.getRunSteps(runId);
    }
}
