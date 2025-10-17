package com.ke.assistant.core.log;

import com.ke.assistant.core.run.ExecutionContext;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class RunLogger {
    public void log(String event, ExecutionContext context) {
        RunLog runLog = new RunLog(event, context);
        log.info(JacksonUtils.serialize(runLog));
    }

    public void log(String event, Map<String, Object> bellaContext, Map<String, Object> additionalInfo) {
        RunLog runLog = new RunLog(event, bellaContext, additionalInfo);
        log.info(JacksonUtils.serialize(runLog));
    }
}
