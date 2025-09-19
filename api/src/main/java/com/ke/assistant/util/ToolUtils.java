package com.ke.assistant.util;

import com.theokanning.openai.assistants.assistant.Tool;
import com.theokanning.openai.response.tool.definition.FunctionTool;
import com.theokanning.openai.response.tool.definition.ToolDefinition;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

    public static List<Tool> convertFromToolDefinition(List<ToolDefinition> toolDefinitions) {
        if(CollectionUtils.isEmpty(toolDefinitions)) {
            return new ArrayList<>();
        }
        return toolDefinitions.stream().map(toolDefinition -> {
            if(toolDefinition.getRealTool() != null) {
                return toolDefinition.getRealTool();
            } else if(toolDefinition instanceof FunctionTool) {
                FunctionTool functionTool = (FunctionTool) toolDefinition;
                Tool.Function function = new Tool.Function();
                Tool.FunctionDefinition definition = new Tool.FunctionDefinition();
                definition.setName(functionTool.getName());
                definition.setStrict(functionTool.getStrict());
                definition.setParameters(functionTool.getParameters());
                definition.setDescription(functionTool.getDescription());
                function.setFunction(definition);
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }
}
