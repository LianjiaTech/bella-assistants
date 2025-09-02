package com.ke.assistant.core.tools;

import com.theokanning.openai.assistants.assistant.Tool;
import com.theokanning.openai.completion.chat.ChatTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具搜索器
 * 负责发现和管理所有ToolHandler实现类
 */
@Component
public class ToolFetcher {
    
    private static final Logger logger = LoggerFactory.getLogger(ToolFetcher.class);

    private final ApplicationContext applicationContext;
    private final Map<String, ToolHandler> toolHandlerMap = new ConcurrentHashMap<>();
    
    public ToolFetcher(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    /**
     * 初始化工具搜索器，扫描所有ToolHandler实现
     */
    @PostConstruct
    public void initialize() {
        searchAndRegisterTools();
    }
    
    /**
     * 搜索并注册所有ToolHandler实现
     */
    private void searchAndRegisterTools() {
        logger.info("开始搜索ToolHandler实现类...");
        
        try {
            // 从Spring容器中获取所有ToolHandler实现的Bean
            Map<String, ToolHandler> handlerBeans = applicationContext.getBeansOfType(ToolHandler.class);
            
            for (Map.Entry<String, ToolHandler> entry : handlerBeans.entrySet()) {
                String beanName = entry.getKey();
                ToolHandler handler = entry.getValue();
                
                try {
                    String toolName = handler.getToolName();
                    if (toolName != null && !toolName.trim().isEmpty()) {
                        toolHandlerMap.put(toolName, handler);
                        logger.info("注册工具: {} -> {} (Bean: {})", 
                            toolName, handler.getClass().getSimpleName(), beanName);
                    } else {
                        logger.warn("工具处理器 {} 返回了空的工具名称，跳过注册", handler.getClass().getSimpleName());
                    }
                } catch (Exception e) {
                    logger.error("注册工具处理器 {} 时发生错误: {}", handler.getClass().getSimpleName(), e.getMessage(), e);
                }
            }
            
            logger.info("工具搜索完成，共注册 {} 个工具", toolHandlerMap.size());
            
        } catch (Exception e) {
            logger.error("搜索ToolHandler实现类时发生错误", e);
        }
    }
    
    /**
     * 根据工具名称获取工具处理器
     * 
     * @param toolName 工具名称
     * @return 工具处理器，如果不存在则返回null
     */
    public ToolHandler getToolHandler(String toolName) {
        return toolHandlerMap.get(toolName);
    }

    /**
     * 获取chat completion使用的Chat tool
     * @return
     */
    public ChatTool fetchChatTool(String toolName) {
        ChatTool chatTool = new ChatTool();
        ToolHandler toolHandler = getToolHandler(toolName);
        if(toolHandler == null) {
            throw new IllegalArgumentException("Unexpected tool type:" + toolName);
        }
        Tool.FunctionDefinition definition = new Tool.FunctionDefinition();
        definition.setName(toolName);
        definition.setDescription(toolHandler.getDescription());
        definition.setParameters(toolHandler.getParameters());
        chatTool.setFunction(definition);
        return chatTool;
    }

}
