package com.ke.assistant.core.tools.handlers.definition;

import com.ke.assistant.core.tools.ToolContext;
import com.ke.assistant.core.tools.ToolDefinitionHandler;
import com.ke.assistant.core.tools.ToolOutputChannel;
import com.ke.assistant.core.tools.ToolStreamEvent;
import com.theokanning.openai.response.stream.CustomToolCallInputDeltaEvent;
import com.theokanning.openai.response.stream.CustomToolCallInputDoneEvent;
import com.theokanning.openai.response.tool.CustomToolCall;
import com.theokanning.openai.response.tool.definition.CustomTool;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@AllArgsConstructor
public class CustomToolHandler implements ToolDefinitionHandler {

    private final static String text_format_desc = "text input according to the tool description and user request.";

    private final static String regex_grammar_desc = "Input data must match regular expression patterns. Patterns are: \n";

    private final static String lark_grammar_desc = "Input data must comply with the defined lark syntax rules. Rules are: \n";

    private final static String regex_tool_desc = "\n The input you generate must strictly comply with the provided regex and do not add any information.";

    private final static String lark_tool_desc = "\n The input you input must strictly follow the Lark syntax, strictly comply with the tool parameter description definition, and not add any additional information.";

    private CustomTool customTool;

    @Override
    public void sendEvent(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {
        String input = arguments.get("input_data").toString();
        CustomToolCall startCall = new CustomToolCall();
        startCall.setCallId(context.getToolId());
        startCall.setName(customTool.getName());
        channel.output(context.getToolId(), ToolStreamEvent.builder()
                .executionStage(ToolStreamEvent.ExecutionStage.prepare)
                .toolCallId(context.getToolId())
                .result(startCall)
                .event(CustomToolCallInputDeltaEvent.builder()
                        .delta(input)
                        .build())
                .build());
        CustomToolCall finalCall = new CustomToolCall();
        finalCall.setCallId(context.getToolId());
        finalCall.setName(customTool.getName());
        finalCall.setInput(input);
        channel.output(context.getToolId(), ToolStreamEvent.builder()
                .executionStage(ToolStreamEvent.ExecutionStage.completed)
                .toolCallId(context.getToolId())
                .result(finalCall)
                .event(CustomToolCallInputDoneEvent.builder()
                        .input(input)
                        .build())
                .build());
    }

    @Override
    public String getToolName() {
        return getToolName(customTool);
    }

    @Override
    public String getDescription() {
        return getDescription(customTool);
    }

    @Override
    public Map<String, Object> getParameters() {
        return getParameters(customTool);
    }

    @Override
    public boolean isFinal() {
        return false;
    }

    public static String getToolName(CustomTool customTool) {
        return customTool.getName();
    }

    public static String getDescription(CustomTool customTool) {
        if(customTool.getFormat().getType().equals("text")) {
            return customTool.getDescription();
        } else {
            CustomTool.GrammarFormat format = (CustomTool.GrammarFormat) customTool.getFormat();
            if("regex".equals(format.getSyntax())) {
                return customTool.getDescription() + regex_tool_desc;
            } else if("lark".equals(format.getSyntax())) {
                return customTool.getDescription() + lark_tool_desc;
            } else {
                return customTool.getDescription();
            }
        }
    }

    public static Map<String, Object> getParameters(CustomTool customTool) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("type", "string");
        String desc;
        if(customTool.getFormat().getType().equals("grammar")) {
            CustomTool.GrammarFormat format = (CustomTool.GrammarFormat) customTool.getFormat();
            if("regex".equals(format.getSyntax())) {
                desc = regex_grammar_desc + format.getDefinition();
                inputData.put("pattern", format.getDefinition());
            } else if("lark".equals(format.getSyntax())) {
                desc = lark_grammar_desc + format.getDefinition();
            } else {
                desc = text_format_desc;
            }
        } else {
            desc = text_format_desc;
        }
        inputData.put("description", desc);
        properties.put("input_data", inputData);

        parameters.put("properties", properties);
        parameters.put("required", Collections.singletonList("input_data"));

        return parameters;
    }


}
