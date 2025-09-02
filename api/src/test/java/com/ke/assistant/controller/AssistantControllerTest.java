package com.ke.assistant.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AssistantController 核心业务逻辑测试
 * 重点测试：多表关联、Tools/Files处理、业务逻辑正确性
 */
class AssistantControllerTest extends BaseControllerTest {

    @Test
    @DisplayName("创建基础Assistant")
    void shouldCreateBasicAssistant() throws Exception {
        String requestBody = loadTestData("assistant-create-basic.json");

        mockMvc.perform(addAuthHeader(post("/v1/assistants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Basic Test Assistant"))
                .andExpect(jsonPath("$.model").value("gpt-4"))
                .andExpect(jsonPath("$.object").value("assistant"))
                .andExpect(jsonPath("$.created_at").isNumber());
    }

    @Test
    @DisplayName("创建带Tools的Assistant - 验证Tools关联")
    void shouldCreateAssistantWithTools() throws Exception {
        String requestBody = loadTestData("assistant-create-with-tools.json");

        MvcResult result = mockMvc.perform(addAuthHeader(post("/v1/assistants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tools").isArray())
                .andExpect(jsonPath("$.tools").isNotEmpty())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String assistantId = json.get("id").asText();

        // 验证Tools确实保存了
        mockMvc.perform(addAuthHeader(get("/v1/assistants/" + assistantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tools[0].type").value("function"))
                .andExpect(jsonPath("$.tools[0].function.name").value("search_web"));
    }

    @Test
    @DisplayName("创建带Files的Assistant - 验证Files关联")
    void shouldCreateAssistantWithFiles() throws Exception {
        String requestBody = loadTestData("assistant-create-with-files.json");

        MvcResult result = mockMvc.perform(addAuthHeader(post("/v1/assistants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.file_ids").isArray())
                .andExpect(jsonPath("$.file_ids").isNotEmpty())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String assistantId = json.get("id").asText();

        // 验证Files关联确实保存了
        mockMvc.perform(addAuthHeader(get("/v1/assistants/" + assistantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.file_ids[0]").value("file_abc123"))
                .andExpect(jsonPath("$.file_ids[1]").value("file_def456"));
    }

    @Test
    @DisplayName("创建带Tool Resources的Assistant - 验证Tool Resources处理")
    void shouldCreateAssistantWithToolResources() throws Exception {
        String requestBody = loadTestData("assistant-create-with-tool-resources.json");

        MvcResult result = mockMvc.perform(addAuthHeader(post("/v1/assistants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool_resources").exists())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String assistantId = json.get("id").asText();

        // 验证Tool Resources正确处理了
        mockMvc.perform(addAuthHeader(get("/v1/assistants/" + assistantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool_resources.code_interpreter.file_ids").isArray());
    }

    @Test
    @DisplayName("更新Assistant的Tools - 验证Tools关联更新")
    void shouldUpdateAssistantTools() throws Exception {
        // 1. 先创建一个带Tools的Assistant
        String createBody = loadTestData("assistant-create-with-tools.json");
        MvcResult createResult = mockMvc.perform(addAuthHeader(post("/v1/assistants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)))
                .andReturn();

        String assistantId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // 2. 更新Tools
        String updateBody = loadTestData("assistant-update-tools.json");
        mockMvc.perform(addAuthHeader(post("/v1/assistants/" + assistantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tools[0].function.name").value("calculate"));

        // 3. 验证原有Tools被替换了
        mockMvc.perform(addAuthHeader(get("/v1/assistants/" + assistantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tools.length()").value(1))
                .andExpect(jsonPath("$.tools[0].function.name").value("calculate"));
    }

    @Test
    @DisplayName("更新Assistant的Files - 验证Files关联更新")  
    void shouldUpdateAssistantFiles() throws Exception {
        // 1. 先创建一个带Files的Assistant
        String createBody = loadTestData("assistant-create-with-files.json");
        MvcResult createResult = mockMvc.perform(addAuthHeader(post("/v1/assistants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)))
                .andReturn();

        String assistantId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // 2. 更新Files
        String updateBody = loadTestData("assistant-update-files.json");
        mockMvc.perform(addAuthHeader(post("/v1/assistants/" + assistantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody)))
                .andExpect(status().isOk());

        // 3. 验证Files关联被正确更新了
        mockMvc.perform(addAuthHeader(get("/v1/assistants/" + assistantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.file_ids.length()").value(1))
                .andExpect(jsonPath("$.file_ids[0]").value("file_new123"));
    }

    @Test
    @DisplayName("同时更新Tools和Files - 验证多表联动更新")
    void shouldUpdateBothToolsAndFiles() throws Exception {
        // 1. 创建带Tools和Files的Assistant
        String createBody = loadTestData("assistant-create-full.json");
        MvcResult createResult = mockMvc.perform(addAuthHeader(post("/v1/assistants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)))
                .andReturn();

        String assistantId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // 2. 同时更新Tools和Files
        String updateBody = loadTestData("assistant-update-tools-and-files.json");
        mockMvc.perform(addAuthHeader(post("/v1/assistants/" + assistantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody)))
                .andExpect(status().isOk());

        // 3. 验证两个关联都被正确更新
        MvcResult getResult = mockMvc.perform(addAuthHeader(get("/v1/assistants/" + assistantId)))
                .andExpect(status().isOk())
                .andReturn();

        String response = getResult.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);

        // 验证Tools更新了
        assert json.get("tools").size() == 2;
        assert json.get("tools").get(0).get("function").get("name").asText().equals("new_function");

        // 验证Files更新了
        assert json.get("file_ids").size() == 3;
    }

    @Test
    @DisplayName("清空Tools和Files - 验证关联删除")
    void shouldClearToolsAndFiles() throws Exception {
        // 1. 创建带Tools和Files的Assistant
        String createBody = loadTestData("assistant-create-full.json");
        MvcResult createResult = mockMvc.perform(addAuthHeader(post("/v1/assistants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)))
                .andReturn();

        String assistantId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // 2. 清空Tools和Files
        String updateBody = loadTestData("assistant-update-clear-tools-files.json");
        mockMvc.perform(addAuthHeader(post("/v1/assistants/" + assistantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody)))
                .andExpect(status().isOk());

        // 3. 验证关联都被清空了
        mockMvc.perform(addAuthHeader(get("/v1/assistants/" + assistantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tools").isEmpty())
                .andExpect(jsonPath("$.file_ids").isEmpty());
    }

    @Test
    @DisplayName("查询不存在的Assistant")
    void shouldReturn404WhenAssistantNotFound() throws Exception {
        mockMvc.perform(addAuthHeader(get("/v1/assistants/asst_nonexistent_id")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("删除Assistant - 验证关联数据清理")
    void shouldDeleteAssistantAndRelations() throws Exception {
        // 1. 创建带关联数据的Assistant
        String createBody = loadTestData("assistant-create-full.json");
        MvcResult createResult = mockMvc.perform(addAuthHeader(post("/v1/assistants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)))
                .andReturn();

        String assistantId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // 2. 删除Assistant
        mockMvc.perform(addAuthHeader(delete("/v1/assistants/" + assistantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assistantId))
                .andExpect(jsonPath("$.deleted").value(true));

        // 3. 验证Assistant和关联数据都被删除
        mockMvc.perform(addAuthHeader(get("/v1/assistants/" + assistantId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("file_ids和tool_resources重复文件去重逻辑验证")
    void shouldDeduplicateFilesWhenFileIdsAndToolResourcesOverlap() throws Exception {
        /*
         * 测试重点：
         * 1. file_ids参数 -> tool_name="_all" 
         * 2. tool_resources参数 -> 具体tool_name (如"code_interpreter")
         * 3. 重复文件去重：toolResourceFiles中与file_ids重复的文件被过滤掉
         * 4. convertToInfo构建：file_ids只包含"_all"的文件，tool_resources构建正确的嵌套结构
         * 
         * 验证场景：
         * - file_ids: ["file_common1", "file_common2"]
         * - tool_resources.code_interpreter.file_ids: ["file_common2", "file_tool_only"]  
         * - 结果：file_common2被去重，只有file_tool_only进入tool_resources
         */
        String requestBody = loadTestData("assistant-create-with-overlapping-files.json");

        MvcResult result = mockMvc.perform(addAuthHeader(post("/v1/assistants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String assistantId = json.get("id").asText();

        // 验证返回的数据结构
        mockMvc.perform(addAuthHeader(get("/v1/assistants/" + assistantId)))
                .andExpect(status().isOk())
                .andExpect(result2 -> {
                    String getResponse = result2.getResponse().getContentAsString();
                    JsonNode getJson = objectMapper.readTree(getResponse);
                    
                    // 验证file_ids只包含tool_name="_all"的文件
                    JsonNode fileIds = getJson.get("file_ids");
                    assert fileIds.isArray();
                    assert fileIds.size() == 2; // 只有file_common1, file_common2 (来自file_ids参数)
                    
                    // 验证tool_resources正确构建了嵌套结构
                    JsonNode toolResources = getJson.get("tool_resources");
                    assert toolResources.has("code_interpreter");
                    
                    // 验证正确的嵌套结构：code_interpreter: { file_ids: [...] }
                    JsonNode codeInterpreter = toolResources.get("code_interpreter");
                    assert codeInterpreter.isObject();
                    assert codeInterpreter.has("file_ids");
                    
                    JsonNode codeFiles = codeInterpreter.get("file_ids");
                    assert codeFiles.isArray();
                    assert codeFiles.size() == 1; // 只有file_tool_only (file_common2被过滤)
                    assert codeFiles.get(0).asText().equals("file_tool_only");
                });
    }

    @Test  
    @DisplayName("更新时file_ids和tool_resources重复文件去重逻辑验证")
    void shouldDeduplicateFilesWhenUpdatingWithOverlappingFiles() throws Exception {
        // 1. 先创建一个Assistant
        String createBody = loadTestData("assistant-create-basic.json");
        MvcResult createResult = mockMvc.perform(addAuthHeader(post("/v1/assistants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)))
                .andReturn();

        String assistantId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // 2. 更新时有重复文件
        String updateBody = loadTestData("assistant-update-with-overlapping-files.json");
        mockMvc.perform(addAuthHeader(post("/v1/assistants/" + assistantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody)))
                .andExpect(status().isOk());

        // 3. 验证去重逻辑正确
        mockMvc.perform(addAuthHeader(get("/v1/assistants/" + assistantId)))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String response = result.getResponse().getContentAsString();
                    JsonNode json = objectMapper.readTree(response);
                    
                    // 验证file_ids只包含tool_name="_all"的文件 
                    JsonNode fileIds = json.get("file_ids");
                    assert fileIds.size() == 2; // file_update1, file_update2
                    
                    // 验证tool_resources正确构建了嵌套结构
                    JsonNode toolResources = json.get("tool_resources");
                    JsonNode codeInterpreter = toolResources.get("code_interpreter");
                    assert codeInterpreter.isObject();
                    assert codeInterpreter.has("file_ids");
                    
                    JsonNode codeFiles = codeInterpreter.get("file_ids");
                    assert codeFiles.isArray();
                    assert codeFiles.size() == 1; // 只有file_tool_unique (file_update1被过滤掉)
                    assert codeFiles.get(0).asText().equals("file_tool_unique");
                });
    }
}
