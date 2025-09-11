package com.ke.assistant.core.log;

import com.ke.assistant.core.file.FileInfo;
import com.ke.assistant.core.run.ExecutionContext;
import com.ke.bella.openapi.BellaContext;
import com.theokanning.openai.assistants.assistant.Tool;
import com.theokanning.openai.assistants.run.ToolFiles;
import com.theokanning.openai.common.LastError;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Data
public class RunLog {
    private String bellaTraceId;
    private String requestId;
    private String akSha;
    private String accountType;
    private String accountCode;
    private String akCode;
    private String parentAkCode;
    private String assistantId;
    private String threadId;
    private String runId;
    private String model;
    private String user;
    private long requestTime; //s
    private long duration;
    private int totalSteps;
    private String assistantMessageId;
    private LastError error;
    private List<Tool> tools;
    private ToolFiles toolFiles;
    private Map<String, FileInfo> fileInfos;
    private boolean isMock;

    public RunLog(ExecutionContext context) {
        // 从ExecutionContext获取BellaContext快照
        Map<String, Object> bellaContextSnapshot = context.getBellaContext();
        
        // 从BellaContext快照中获取headers
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) bellaContextSnapshot.get("headers");
        if (headers != null) {
            this.bellaTraceId = headers.get(BellaContext.BELLA_TRACE_HEADER);
            this.requestId = headers.get(BellaContext.BELLA_REQUEST_ID_HEADER);
            this.isMock = headers.containsKey(BellaContext.BELLA_REQUEST_MOCK_HEADER);
        }
        
        // 从BellaContext快照中获取ApiKey信息
        com.ke.bella.openapi.apikey.ApikeyInfo apikeyInfo = 
            (com.ke.bella.openapi.apikey.ApikeyInfo) bellaContextSnapshot.get("ak");
        if (apikeyInfo != null) {
            this.akCode = apikeyInfo.getCode();
            this.parentAkCode = apikeyInfo.getParentCode();
            this.accountCode = apikeyInfo.getOwnerCode();
            this.accountType = apikeyInfo.getOwnerType();
            this.akSha = apikeyInfo.getAkSha();
        }
        
        // 从ExecutionContext获取run相关信息
        if (context.getRun() != null) {
            this.assistantId = context.getAssistantId();
            this.threadId = context.getThreadId();
            this.runId = context.getRunId();
            this.model = context.getModel();
            this.user = context.getUser();
        }
        
        // 设置执行时间相关信息
        if (context.getStartTime() != null) {
            this.requestTime = context.getStartTime().atZone(ZoneId.systemDefault()).toEpochSecond();
            this.duration = LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond() - this.requestTime;
        }
        // 执行的数据信息
        this.fileInfos = context.getFileInfos();
        this.tools = context.getTools();
        this.toolFiles = context.getToolFiles();
        this.totalSteps = context.getCurrentStep();
        this.assistantMessageId = context.getAssistantMessageId();
        this.error = context.getLastError();
    }
}
