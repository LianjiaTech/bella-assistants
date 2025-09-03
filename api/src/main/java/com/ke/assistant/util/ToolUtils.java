package com.ke.assistant.util;

import com.theokanning.openai.assistants.assistant.Tool;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.Assert;

import java.util.List;

public class ToolUtils {

    public static void checkTools(List<Tool> toolList) {
        if(CollectionUtils.isNotEmpty(toolList)) {
            toolList.forEach(ToolUtils::checkTool);
        }
    }

    public static void checkTool(Tool tool) {
        if(tool instanceof Tool.Function) {
            Tool.Function functionTool = (Tool.Function) tool;
            Assert.notNull(functionTool.getFunction(), "function is null");
            Assert.hasText(functionTool.getFunction().getName(), "function name is null");
            Assert.notNull(functionTool.getFunction().getParameters(), "function parameter can not be null");
        }
    }
}
