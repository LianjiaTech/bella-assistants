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
}
