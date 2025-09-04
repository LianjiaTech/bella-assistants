package com.ke.assistant.core.plan.template;

import com.ke.assistant.core.run.ExecutionContext;
import com.ke.assistant.core.file.FileInfo;
import com.theokanning.openai.assistants.assistant.Tool;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Template context builder service for constructing Jinja template context
 */
public class TemplateContextBuilder {

    /**
     * Build template context from execution context
     *
     * @param context Execution context containing all necessary information
     * @return Template context for Jinja rendering
     */
    public static TemplateContext buildTemplateContext(ExecutionContext context) {
        return TemplateContext.builder()
                .env(buildEnvironmentInfo())
                .user(buildUserInfo(context))
                .agent(buildAgentInfo(context))
                .profile(buildProfileInfo(context))
                .build();
    }

    /**
     * Build environment information
     */
    private static TemplateContext.EnvironmentInfo buildEnvironmentInfo() {
        return TemplateContext.EnvironmentInfo.builder()
                .datetime(LocalDateTime.now())
                .build();
    }

    /**
     * Build user information from context
     */
    private static TemplateContext.UserInfo buildUserInfo(ExecutionContext context) {
        return null;
    }

    /**
     * Build agent information from context
     */
    private static TemplateContext.AgentInfo buildAgentInfo(ExecutionContext context) {
        List<String> toolNames = new ArrayList<>();
        List<TemplateContext.ToolFileInfo> files = new ArrayList<>();

        if(context.getToolFiles() != null && context.getToolFiles().getTools() != null) {
            context.getToolFiles().getTools().forEach(
                    (tool, fileIds) -> {
                        fileIds.forEach(id -> {
                            FileInfo fileInfo = context.getFileInfos().get(id);
                            TemplateContext.ToolFileInfo toolFileInfo = TemplateContext.ToolFileInfo.builder()
                                    .id(id)
                                    .tool(tool)
                                    .name(fileInfo == null ? null : fileInfo.getName())
                                    .abstractInfo(fileInfo == null ? null : fileInfo.getAbstractInfo())
                                    .build();
                            files.add(toolFileInfo);
                        });
                    }
            );
        }

        // Extract tool names from the tools available in context
        if (context.getTools() != null) {
            toolNames = context.getTools().stream()
                    .map(Tool::getType)
                    .collect(Collectors.toList());
        }

        return TemplateContext.AgentInfo.builder()
                .instruction(context.getInstructions())
                .toolNames(toolNames)
                .files(files)
                .build();
    }


    /**
     * Build profile information
     */
    private static List<TemplateContext.ProfileInfo> buildProfileInfo(ExecutionContext context) {
        return new ArrayList<>();
    }
}
