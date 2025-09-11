package com.ke.assistant.core.log;

import com.ke.assistant.core.run.ExecutionContext;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RunLogger {
    public void log(ExecutionContext context) {
        RunLog runLog = new RunLog(context);
        log.info(JacksonUtils.serialize(runLog));
    }
}
