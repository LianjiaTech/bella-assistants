package com.ke.assistant.core.log;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.ke.assistant.core.run.ExecutionContext;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RunLogger {
    public void log(String event, ExecutionContext context, Map<String, Object> bellaContext) {
        RunLog runLog = new RunLog(event, context, bellaContext);
        log.info(JacksonUtils.serialize(runLog));
    }

    public void log(String event, Map<String, Object> bellaContext, Map<String, Object> additionalInfo) {
        RunLog runLog = new RunLog(event, bellaContext, additionalInfo);
        log.info(JacksonUtils.serialize(runLog));
    }
}
