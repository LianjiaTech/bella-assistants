package com.ke.assistant.core.tools;

import java.util.List;
import java.util.Map;

import com.theokanning.openai.assistants.assistant.Tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ToolContext {
    private Tool tool;
    private String toolId;
    private List<String> files;
    private String user;
}
