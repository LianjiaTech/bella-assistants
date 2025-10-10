package com.ke.assistant.core.tools.handlers.definition;

import com.ke.assistant.core.tools.ToolContext;
import com.ke.assistant.core.tools.ToolDefinitionHandler;
import com.ke.assistant.core.tools.ToolOutputChannel;
import com.ke.assistant.core.tools.ToolStreamEvent;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.response.ItemStatus;
import com.theokanning.openai.response.tool.LocalShellToolCall;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;

/**
 * Local Shell tool definition handler Server does not execute the shell; it only emits the tool call event compatible with Response API stream.
 */
@Slf4j
@Component
public class LocalShellHandler implements ToolDefinitionHandler {
    @Override
    public void sendEvent(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {
        // Build LocalShell tool call from arguments
        LocalShellToolCall startCall = buildLocalShellCall(context, arguments, ItemStatus.IN_PROGRESS);

        // Send: output item added
        channel.output(context.getToolId(), ToolStreamEvent.builder().toolCallId(context.getToolId())
                .executionStage(ToolStreamEvent.ExecutionStage.prepare)
                .result(startCall)
                .build());

        // Send: output item done and complete the tool call
        LocalShellToolCall finalCall = buildLocalShellCall(context, arguments, ItemStatus.COMPLETED);
        channel.output(context.getToolId(), ToolStreamEvent.builder().toolCallId(context.getToolId())
                .executionStage(ToolStreamEvent.ExecutionStage.completed)
                .result(finalCall)
                .build());
    }

    @SuppressWarnings("unchecked")
    private LocalShellToolCall buildLocalShellCall(ToolContext context, Map<String, Object> arguments, ItemStatus status) {
        LocalShellToolCall.ShellAction action = new LocalShellToolCall.ShellAction();

        // command can be array or string; action expects a List<String>
        Object cmdObj = arguments.get("command");
        if(cmdObj instanceof Collection) {
            Collection<Object> cmdList = (Collection<Object>) cmdObj;
            action.setCommand(cmdList.stream().map(String::valueOf).collect(Collectors.toList()));
        } else if(cmdObj instanceof String) {
            action.setCommand(singletonList((String) cmdObj));
        } else if(cmdObj != null) {
            // Fallback: serialize unknown structure as single element
            action.setCommand(singletonList(JacksonUtils.serialize(cmdObj)));
        }

        // working_directory
        Object wd = arguments.get("working_directory");
        if(wd != null) {
            action.setWorkingDirectory(String.valueOf(wd));
        }

        // env: pass through as provided (Object per spec)
        if(arguments.containsKey("env")) {
            action.setEnv(arguments.get("env"));
        } else {
            action.setEnv(new HashMap<>());
        }

        // optional user
        Object user = arguments.get("user");
        if(user != null) {
            action.setUser(String.valueOf(user));
        }

        // optional timeout_ms
        Object timeout = arguments.get("timeout_ms");
        if(timeout instanceof Number) {
            action.setTimeout_ms(((Number) timeout).intValue());
        } else if(timeout instanceof String) {
            try {
                action.setTimeout_ms(Integer.parseInt((String) timeout));
            } catch (NumberFormatException ignore) {
                // ignore invalid timeout value
            }
        }

        LocalShellToolCall call = new LocalShellToolCall();
        call.setStatus(status);
        call.setCallId(context.getToolId());
        call.setAction(action);
        return call;
    }

    // No conversion helpers needed; env is forwarded as provided per spec

    @Override
    public String getToolName() {
        return "local_shell";
    }

    @Override
    public String getDescription() {
        return "Execute shell commands on the local system with full control over execution environment. " +
                "Supports running commands as different users, setting environment variables, " +
                "specifying working directories, and timeout control. " +
                "Use this tool to interact with the file system, run scripts, check system status, " +
                "install software, or execute any command-line operations needed.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        // Command parameter
        Map<String, Object> commandParam = new HashMap<>();
        commandParam.put("type", "array");
        Map<String, Object> items = new HashMap<>();
        items.put("type", "string");
        commandParam.put("items", items);
        commandParam.put("description",
                "The shell command to execute as an array of strings. " +
                        "The first element should be the command/program name, followed by its arguments. " +
                        "Examples: [\"ls\", \"-la\"], [\"python3\", \"script.py\"], [\"curl\", \"-s\", \"https://api.example.com\"]");
        properties.put("command", commandParam);

        // Environment variables
        Map<String, Object> envParam = new HashMap<>();
        envParam.put("type", "object");
        envParam.put("description",
                "Environment variables to set for the command execution. " +
                        "These variables will be available to the command during execution. " +
                        "Use this to pass configuration, API keys, or modify PATH. " +
                        "Example: {\"NODE_ENV\": \"production\", \"API_KEY\": \"secret\", \"PATH\": \"/usr/local/bin:/usr/bin\"}");
        properties.put("env", envParam);

        // Timeout
        Map<String, Object> timeoutParam = new HashMap<>();
        timeoutParam.put("type", "integer");
        timeoutParam.put("description",
                "Maximum execution time in milliseconds before the command is terminated. " +
                        "Use this to prevent long-running commands from hanging. " +
                        "Common values: 5000 (5 seconds), 30000 (30 seconds), 300000 (5 minutes). " +
                        "If not specified, a default timeout will be applied.");
        properties.put("timeout_ms", timeoutParam);

        // User
        Map<String, Object> userParam = new HashMap<>();
        userParam.put("type", "string");
        userParam.put("description",
                "The system user account under which to execute the command. " +
                        "This allows running commands with different privileges or in different user contexts. " +
                        "Common users: \"root\" (admin), \"www-data\" (web server), \"nobody\" (minimal privileges), \"postgres\" (database). " +
                        "If not specified, runs as the current process user.");
        properties.put("user", userParam);

        // Working directory
        Map<String, Object> wdParam = new HashMap<>();
        wdParam.put("type", "string");
        wdParam.put("description",
                "The directory path where the command should be executed. " +
                        "This affects relative file paths used in the command and where output files are created. " +
                        "Should be an absolute path like \"/home/user/project\" or \"/tmp\". " +
                        "If not specified, uses the current working directory of the process.");
        properties.put("working_directory", wdParam);

        parameters.put("properties", properties);
        parameters.put("required", singletonList("command"));

        return parameters;
    }

    @Override
    public boolean isFinal() {
        return false;
    }
}
