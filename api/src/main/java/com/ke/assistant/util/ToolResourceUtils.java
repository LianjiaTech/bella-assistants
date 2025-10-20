package com.ke.assistant.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;

import com.theokanning.openai.assistants.assistant.CodeInterpreterResources;
import com.theokanning.openai.assistants.assistant.FileSearchResources;
import com.theokanning.openai.assistants.assistant.FunctionResources;
import com.theokanning.openai.assistants.assistant.ToolResources;
import com.theokanning.openai.assistants.vector_store.VectorStore;

public class ToolResourceUtils {

    /**
     * 将 tool_resources 转换为 Map 列表
     *
     * @param toolResources tool_resources 结构
     *
     * @return Map 列表，每个Map包含 file_id 和 tool_name
     */
    public static List<Map<String, String>> toolResourceToFiles(ToolResources toolResources) {
        List<Map<String, String>> fileList = new ArrayList<>();

        if(toolResources == null) {
            return fileList;
        }

        // 处理 code_interpreter
        if(toolResources.getCodeInterpreter() != null) {
            if(CollectionUtils.isNotEmpty(toolResources.getCodeInterpreter().getFileIds())) {
                for (String fileId : toolResources.getCodeInterpreter().getFileIds()) {
                    Map<String, String> fileMap = new HashMap<>();
                    fileMap.put("file_id", fileId);
                    fileMap.put("tool_name", "code_interpreter");
                    fileList.add(fileMap);
                }
            }
        }

        // 处理 file_search
        if(toolResources.getFileSearch() != null) {

            // 处理 vector_stores（用于创建新的vector store）
            if(toolResources.getFileSearch().getVectorStores() != null) {
                for (VectorStore vectorStore : toolResources.getFileSearch().getVectorStores()) {
                    if(CollectionUtils.isNotEmpty(vectorStore.getFileIds())) {
                        for (String fileId : vectorStore.getFileIds()) {
                            Map<String, String> fileMap = new HashMap<>();
                            fileMap.put("file_id", fileId);
                            fileMap.put("tool_name", "file_search");
                            fileList.add(fileMap);
                        }
                    }
                }
            }
        }

        // 处理 functions
        if(toolResources.getFunctions() != null) {
            for (FunctionResources functionResource : toolResources.getFunctions()) {
                String functionName = functionResource.getName();
                if(CollectionUtils.isNotEmpty(functionResource.getFileIds())) {
                    for (String fileId : functionResource.getFileIds()) {
                        Map<String, String> fileMap = new HashMap<>();
                        fileMap.put("file_id", fileId);
                        fileMap.put("tool_name", functionName);
                        fileList.add(fileMap);
                    }
                }
            }
        }

        return fileList;
    }

    /**
     * 将文件关联列表重新构建为 tool_resources 结构
     * 
     * @param files 文件关联列表
     * @return tool_resources 嵌套结构
     */
    public static ToolResources buildToolResourcesFromFiles(List<Map<String, String>> files) {
        
        if(files == null || files.isEmpty()) {
            return null;
        }
        ToolResources toolResources = new ToolResources();

        // 按tool_name分组文件
        Map<String, List<String>> toolFileMap = new HashMap<>();
        for (Map<String, String> file : files) {
            String toolName = file.get("tool_name");
            String fileId = file.get("file_id");
            
            if(toolName != null && fileId != null && !"_all".equals(toolName)) {
                toolFileMap.computeIfAbsent(toolName, k -> new ArrayList<>()).add(fileId);
            }
        }
        
        // 构建嵌套结构
        for (Map.Entry<String, List<String>> entry : toolFileMap.entrySet()) {
            String toolName = entry.getKey();
            List<String> fileIds = entry.getValue();
            
            if("code_interpreter".equals(toolName)) {
                // code_interpreter: { file_ids: [...] }
                CodeInterpreterResources codeInterpreter = new CodeInterpreterResources();
                codeInterpreter.setFileIds(fileIds);

                toolResources.setCodeInterpreter(codeInterpreter);
                
            } else if("file_search".equals(toolName)) {
                // file_search: { vector_stores: [{ file_ids: [...] }] }
                VectorStore vectorStore = new VectorStore();
                vectorStore.setFileIds(fileIds);
                
                List<VectorStore> vectorStores = new ArrayList<>();
                vectorStores.add(vectorStore);
                
                FileSearchResources fileSearch = new FileSearchResources();
                fileSearch.setVectorStores(vectorStores);
                toolResources.setFileSearch(fileSearch);
                
            } else {
                // functions: [{ name: toolName, file_ids: [...] }]
                FunctionResources function = new FunctionResources();
                function.setName(toolName);
                function.setFileIds(fileIds);
                
                List<FunctionResources> functions = toolResources.getFunctions();
                if(functions == null) {
                    functions = new ArrayList<>();
                    toolResources.setFunctions(functions);
                }
                functions.add(function);
            }
        }
        
        return toolResources;
    }
    
    /**
     * 将 ToolResources 转换为 ToolFiles
     * 
     * @param toolResources ToolResources 对象
     * @return 格式为 {"toolName": ["file_id1", "file_id2"]}
     */
    public static Map<String, List<String>> toolResourcesToToolFiles(ToolResources toolResources) {
        if (toolResources == null) {
            return new HashMap<>();
        }

        Map<String, List<String>> toolsMap = new HashMap<>();
        
        // 处理 code_interpreter
        if (toolResources.getCodeInterpreter() != null && 
            CollectionUtils.isNotEmpty(toolResources.getCodeInterpreter().getFileIds())) {
            toolsMap.put("code_interpreter", new ArrayList<>(toolResources.getCodeInterpreter().getFileIds()));
        }
        
        // 处理 file_search
        if (toolResources.getFileSearch() != null) {
            List<String> fileSearchFiles = new ArrayList<>();
            
            // 从 vector_stores 中收集文件ID
            if (toolResources.getFileSearch().getVectorStores() != null) {
                for (VectorStore vectorStore : toolResources.getFileSearch().getVectorStores()) {
                    if (CollectionUtils.isNotEmpty(vectorStore.getFileIds())) {
                        fileSearchFiles.addAll(vectorStore.getFileIds());
                    }
                }
            }
            
            if (!fileSearchFiles.isEmpty()) {
                toolsMap.put("file_search", fileSearchFiles);
            }
        }
        
        // 处理 functions
        if (toolResources.getFunctions() != null) {
            for (FunctionResources functionResource : toolResources.getFunctions()) {
                String functionName = functionResource.getName();
                if (functionName != null && CollectionUtils.isNotEmpty(functionResource.getFileIds())) {
                    toolsMap.put(functionName, new ArrayList<>(functionResource.getFileIds()));
                }
            }
        }

        return toolsMap;
    }
}
