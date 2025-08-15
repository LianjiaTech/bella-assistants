package com.ke.assistant.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolResourceUtils {

    /**
     * 将 tool_resources 转换为 Map 列表
     *
     * @param toolResources tool_resources 结构
     *
     * @return Map 列表，每个Map包含 file_id 和 tool_name
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, String>> toolResourceToFiles(Map<String, Object> toolResources) {
        List<Map<String, String>> fileList = new ArrayList<>();

        if(toolResources == null) {
            return fileList;
        }

        // 处理 code_interpreter
        if(toolResources.containsKey("code_interpreter")) {
            Map<String, Object> codeInterpreter = (Map<String, Object>) toolResources.get("code_interpreter");
            if(codeInterpreter.containsKey("file_ids")) {
                List<String> fileIds = (List<String>) codeInterpreter.get("file_ids");
                for (String fileId : fileIds) {
                    Map<String, String> fileMap = new HashMap<>();
                    fileMap.put("file_id", fileId);
                    fileMap.put("tool_name", "code_interpreter");
                    fileList.add(fileMap);
                }
            }
        }

        // 处理 file_search
        if(toolResources.containsKey("file_search")) {
            Map<String, Object> fileSearch = (Map<String, Object>) toolResources.get("file_search");

            // 处理 vector_stores（用于创建新的vector store）
            if(fileSearch.containsKey("vector_stores")) {
                List<Map<String, Object>> vectorStores = (List<Map<String, Object>>) fileSearch.get("vector_stores");
                for (Map<String, Object> vectorStore : vectorStores) {
                    if(vectorStore.containsKey("file_ids")) {
                        List<String> fileIds = (List<String>) vectorStore.get("file_ids");
                        for (String fileId : fileIds) {
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
        if(toolResources.containsKey("functions")) {
            List<Map<String, Object>> functions = (List<Map<String, Object>>) toolResources.get("functions");
            for (Map<String, Object> function : functions) {
                String functionName = (String) function.get("name");
                if(function.containsKey("file_ids")) {
                    List<String> fileIds = (List<String>) function.get("file_ids");
                    for (String fileId : fileIds) {
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
    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildToolResourcesFromFiles(List<Map<String, String>> files) {
        Map<String, Object> toolResources = new HashMap<>();
        
        if(files == null || files.isEmpty()) {
            return toolResources;
        }
        
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
                Map<String, Object> codeInterpreter = new HashMap<>();
                codeInterpreter.put("file_ids", fileIds);
                toolResources.put("code_interpreter", codeInterpreter);
                
            } else if("file_search".equals(toolName)) {
                // file_search: { vector_stores: [{ file_ids: [...] }] }
                Map<String, Object> vectorStore = new HashMap<>();
                vectorStore.put("file_ids", fileIds);
                
                List<Map<String, Object>> vectorStores = new ArrayList<>();
                vectorStores.add(vectorStore);
                
                Map<String, Object> fileSearch = new HashMap<>();
                fileSearch.put("vector_stores", vectorStores);
                toolResources.put("file_search", fileSearch);
                
            } else {
                // functions: [{ name: toolName, file_ids: [...] }]
                Map<String, Object> function = new HashMap<>();
                function.put("name", toolName);
                function.put("file_ids", fileIds);
                
                List<Map<String, Object>> functions = (List<Map<String, Object>>) toolResources.get("functions");
                if(functions == null) {
                    functions = new ArrayList<>();
                    toolResources.put("functions", functions);
                }
                functions.add(function);
            }
        }
        
        return toolResources;
    }
}
