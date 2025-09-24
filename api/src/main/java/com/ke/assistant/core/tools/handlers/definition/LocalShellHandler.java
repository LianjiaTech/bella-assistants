package com.ke.assistant.core.tools.handlers.definition;

import com.ke.assistant.core.tools.ToolContext;
import com.ke.assistant.core.tools.ToolDefinitionHandler;
import com.ke.assistant.core.tools.ToolOutputChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

//todo: implement LocalShellHandler
@Slf4j
@Component
public class LocalShellHandler implements ToolDefinitionHandler {
    @Override
    public void sendEvent(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {

    }

    @Override
    public String getToolName() {
        return "";
    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Collections.emptyMap();
    }

    @Override
    public boolean isFinal() {
        return false;
    }
}
