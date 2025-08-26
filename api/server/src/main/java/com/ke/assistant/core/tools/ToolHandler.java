package com.ke.assistant.core.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.theokanning.openai.assistants.assistant.Tool;

import java.util.Map;

/**
 * 工具处理器接口
 * 定义内部工具执行的统一接口
 */
public interface ToolHandler {
    
    /**
     * 执行工具调用
     *
     * @param tool 工具定义
     * @param arguments 工具入参
     * @param channel 用于结果输出
     * @return 工具执行结果 -  除CodeInterpreter和FileSearch外，ToolResult的output都为String
     */
    ToolResult execute(Tool tool, JsonNode arguments, ToolOutputChannel channel);
    
    /**
     * 获取工具名称
     * 
     * @return 工具名称
     */
    String getToolName();
    
    /**
     * 获取工具描述
     * 
     * @return 工具描述
     */
    String getDescription();


    /**
     * 获取工具参数
     *
     * @return 工具参数
     */
    Map<String, Object> getParameters();

    
    /**
     * 处理工具输出消息
     * 允许工具对输出结果进行后处理
     * 
     * @param output 原始输出
     * @return 处理后的输出
     */
    default Object processOutputMessage(Object output) {
        return output;
    }

    /**
     * 检查是否为终结工具
     * 终结工具的输出会直接作为assistant消息发送
     *
     * @return 是否为终结工具
     */
    boolean isFinal();

}
