package com.ke.assistant.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ThreadController 核心业务逻辑测试
 * 重点测试：Tool Resources关联、初始消息创建、权限控制、复杂操作(Fork/Copy/Merge)
 */
class ThreadControllerTest extends BaseControllerTest {

    @Test
    @DisplayName("创建基础Thread")
    void shouldCreateBasicThread() throws Exception {
        String requestBody = loadTestData("thread-create-basic.json");

        mockMvc.perform(addAuthHeader(post("/v1/threads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.object").value("thread"))
                .andExpect(jsonPath("$.created_at").isNumber());
    }

    @Test
    @DisplayName("创建带Tool Resources的Thread - 验证文件关联")
    void shouldCreateThreadWithToolResources() throws Exception {
        String requestBody = loadTestData("thread-create-with-tool-resources.json");

        MvcResult result = mockMvc.perform(addAuthHeader(post("/v1/threads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool_resources").exists())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String threadId = json.get("id").asText();

        // 验证Tool Resources正确保存了
        mockMvc.perform(addAuthHeader(get("/v1/threads/" + threadId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool_resources.code_interpreter.file_ids").isArray())
                .andExpect(jsonPath("$.tool_resources.code_interpreter..file_ids[0]").value("file_abc123"));
    }

    @Test
    @DisplayName("创建带初始消息的Thread - 验证消息自动创建")
    void shouldCreateThreadWithInitialMessages() throws Exception {
        String requestBody = loadTestData("thread-create-with-messages.json");

        MvcResult result = mockMvc.perform(addAuthHeader(post("/v1/threads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String threadId = json.get("id").asText();

        // 验证初始消息被创建了
        mockMvc.perform(addAuthHeader(get("/v1/threads/" + threadId + "/messages")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andExpect(jsonPath("$.data[0].role").value("assistant"))
                .andExpect(jsonPath("$.data[0].content").exists());
    }

    @Test
    @DisplayName("更新Thread的Tool Resources - 验证文件关联更新")
    void shouldUpdateThreadToolResources() throws Exception {
        // 1. 先创建一个带Tool Resources的Thread
        String createBody = loadTestData("thread-create-with-tool-resources.json");
        MvcResult createResult = mockMvc.perform(addAuthHeader(post("/v1/threads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)))
                .andReturn();

        String threadId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // 2. 更新Tool Resources
        String updateBody = loadTestData("thread-update-tool-resources.json");
        mockMvc.perform(addAuthHeader(post("/v1/threads/" + threadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody)))
                .andExpect(status().isOk());

        // 3. 验证Tool Resources被正确更新了
        mockMvc.perform(addAuthHeader(get("/v1/threads/" + threadId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool_resources.code_interpreter.file_ids.length()").value(1))
                .andExpect(jsonPath("$.tool_resources.code_interpreter.file_ids[0]").value("file_new123"));
    }

    @Test
    @DisplayName("更新Thread的metadata和environment - 验证JSON更新")
    void shouldUpdateThreadMetadataAndEnvironment() throws Exception {
        // 1. 先创建一个Thread
        String createBody = loadTestData("thread-create-basic.json");
        MvcResult createResult = mockMvc.perform(addAuthHeader(post("/v1/threads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)))
                .andReturn();

        String threadId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // 2. 更新metadata和environment
        String updateBody = loadTestData("thread-update-metadata-environment.json");
        mockMvc.perform(addAuthHeader(post("/v1/threads/" + threadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody)))
                .andExpect(status().isOk());

        // 3. 验证更新生效
        MvcResult getResult = mockMvc.perform(addAuthHeader(get("/v1/threads/" + threadId)))
                .andExpect(status().isOk())
                .andReturn();

        String response = getResult.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);

        // 验证environment更新了
        assert json.get("environment").get("mode").asText().equals("production");
        assert json.get("environment").get("timeout").asInt() == 30;
    }

    @Test
    @DisplayName("Fork Thread - 验证完整复制功能")
    void shouldForkThreadSuccessfully() throws Exception {
        // 1. 创建原始Thread（带消息和文件）
        String createBody = loadTestData("thread-create-full.json");
        MvcResult createResult = mockMvc.perform(addAuthHeader(post("/v1/threads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)))
                .andReturn();

        String originalThreadId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // 2. Fork Thread
        MvcResult forkResult = mockMvc.perform(addAuthHeader(post("/v1/threads/" + originalThreadId + "/fork")))
                .andExpect(status().isOk())
                .andReturn();

        String forkResponse = forkResult.getResponse().getContentAsString();
        JsonNode forkJson = objectMapper.readTree(forkResponse);
        String forkedThreadId = forkJson.get("id").asText();

        // 3. 验证Fork的Thread是新的ID
        assert !originalThreadId.equals(forkedThreadId);

        // 4. 验证Fork的Thread有相同的tool_resources
        mockMvc.perform(addAuthHeader(get("/v1/threads/" + forkedThreadId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool_resources").exists());

        // 5. 验证Fork的Thread有复制的消息
        mockMvc.perform(addAuthHeader(get("/v1/threads/" + forkedThreadId + "/messages")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isNotEmpty());
    }

    @Test
    @DisplayName("Copy Thread - 验证消息复制功能")
    void shouldCopyThreadMessagesSuccessfully() throws Exception {
        // 1. 创建源Thread（带消息）
        String createSourceBody = loadTestData("thread-create-with-messages.json");
        MvcResult createSourceResult = mockMvc.perform(addAuthHeader(post("/v1/threads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createSourceBody)))
                .andReturn();

        String fromThreadId = objectMapper.readTree(createSourceResult.getResponse().getContentAsString())
                .get("id").asText();

        // 2. 创建目标Thread（空的）
        String createTargetBody = loadTestData("thread-create-basic.json");
        MvcResult createTargetResult = mockMvc.perform(addAuthHeader(post("/v1/threads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createTargetBody)))
                .andReturn();

        String toThreadId = objectMapper.readTree(createTargetResult.getResponse().getContentAsString())
                .get("id").asText();

        // 3. 复制消息
        mockMvc.perform(addAuthHeader(post("/v1/threads/" + fromThreadId + "/copy_to/" + toThreadId)))
                .andExpect(status().isOk());

        // 4. 验证目标Thread有了复制的消息
        mockMvc.perform(addAuthHeader(get("/v1/threads/" + toThreadId + "/messages")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andExpect(jsonPath("$.data[0].role").value("assistant"));
    }

    @Test
    @DisplayName("Merge Thread - 验证智能合并功能")
    void shouldMergeThreadMessagesIntelligently() throws Exception {
        // 1. 创建源Thread（带消息）
        String createSourceBody = loadTestData("thread-create-with-messages.json");
        MvcResult createSourceResult = mockMvc.perform(addAuthHeader(post("/v1/threads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createSourceBody)))
                .andReturn();

        String fromThreadId = objectMapper.readTree(createSourceResult.getResponse().getContentAsString())
                .get("id").asText();

        // 2. 创建目标Thread（也带消息）
        String createTargetBody = loadTestData("thread-create-with-messages.json");
        MvcResult createTargetResult = mockMvc.perform(addAuthHeader(post("/v1/threads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createTargetBody)))
                .andReturn();

        String toThreadId = objectMapper.readTree(createTargetResult.getResponse().getContentAsString())
                .get("id").asText();

        // 3. 先复制一次（建立重复消息）
        mockMvc.perform(addAuthHeader(post("/v1/threads/" + fromThreadId + "/copy_to/" + toThreadId)))
                .andExpect(status().isOk());

        // 获取复制后的消息数量
        MvcResult afterCopyResult = mockMvc.perform(addAuthHeader(get("/v1/threads/" + toThreadId + "/messages")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode afterCopyJson = objectMapper.readTree(afterCopyResult.getResponse().getContentAsString());
        int messagesAfterCopy = afterCopyJson.get("data").size();

        // 4. 再次合并（应该避免重复）
        mockMvc.perform(addAuthHeader(post("/v1/threads/" + fromThreadId + "/merge_to/" + toThreadId)))
                .andExpect(status().isOk());

        // 5. 验证没有产生重复消息（智能去重）
        MvcResult afterMergeResult = mockMvc.perform(addAuthHeader(get("/v1/threads/" + toThreadId + "/messages")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode afterMergeJson = objectMapper.readTree(afterMergeResult.getResponse().getContentAsString());
        int messagesAfterMerge = afterMergeJson.get("data").size();
        
        // 验证消息数量没有增加（去重生效）
        assertEquals(messagesAfterCopy, messagesAfterMerge, "Merge should not create duplicate messages");
    }

    @Test
    @DisplayName("Merge Thread重复检测 - 验证MessageUtils逻辑")  
    void shouldDetectDuplicatesCorrectlyInMerge() throws Exception {
        /*
         * 测试重点：
         * 1. 验证MessageUtils.getExistingSourceIds的正确性
         * 2. 验证MessageUtils.isMessageExists的重复检测逻辑
         * 3. 确保copy_message_id元数据被正确处理
         * 
         * 验证场景：
         * - Thread A: 原始消息 msg_1, msg_2
         * - Thread B: 空Thread
         * - Copy A到B: B有msg_1_copy, msg_2_copy (带copy_message_id)
         * - Merge A到B: 应该检测到重复，不再复制
         */
        
        // 1. 创建源Thread（带2条消息） 
        String createSourceBody = loadTestData("thread-create-with-messages.json");
        MvcResult createSourceResult = mockMvc.perform(addAuthHeader(post("/v1/threads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createSourceBody)))
                .andReturn();

        String fromThreadId = objectMapper.readTree(createSourceResult.getResponse().getContentAsString())
                .get("id").asText();

        // 2. 创建空的目标Thread
        String createTargetBody = loadTestData("thread-create-basic.json");
        MvcResult createTargetResult = mockMvc.perform(addAuthHeader(post("/v1/threads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createTargetBody)))
                .andReturn();

        String toThreadId = objectMapper.readTree(createTargetResult.getResponse().getContentAsString())
                .get("id").asText();

        // 3. 复制消息到目标Thread
        mockMvc.perform(addAuthHeader(post("/v1/threads/" + fromThreadId + "/copy_to/" + toThreadId)))
                .andExpect(status().isOk());

        // 4. 验证目标Thread有复制的消息（带copy_message_id）
        MvcResult messagesResult = mockMvc.perform(addAuthHeader(get("/v1/threads/" + toThreadId + "/messages")))
                .andExpect(status().isOk())
                .andReturn();

        String messagesResponse = messagesResult.getResponse().getContentAsString();
        JsonNode messagesJson = objectMapper.readTree(messagesResponse);
        int originalCount = messagesJson.get("data").size();
        
        // 验证至少有一条消息
        assertTrue(originalCount > 0, "Target thread should have copied messages");

        // 5. 尝试Merge（应该检测到重复，不添加新消息）
        mockMvc.perform(addAuthHeader(post("/v1/threads/" + fromThreadId + "/merge_to/" + toThreadId)))
                .andExpect(status().isOk());

        // 6. 验证消息数量没有变化（去重成功）
        MvcResult afterMergeResult = mockMvc.perform(addAuthHeader(get("/v1/threads/" + toThreadId + "/messages")))
                .andExpect(status().isOk())
                .andReturn();

        String afterMergeResponse = afterMergeResult.getResponse().getContentAsString();
        JsonNode afterMergeJson = objectMapper.readTree(afterMergeResponse);
        int afterMergeCount = afterMergeJson.get("data").size();

        assertEquals(originalCount, afterMergeCount, 
            "MessageUtils should correctly detect duplicates and prevent re-copying");
    }

    @Test
    @DisplayName("获取不存在的Thread")
    void shouldReturn404WhenThreadNotFound() throws Exception {
        mockMvc.perform(addAuthHeader(get("/v1/threads/thread_nonexistent_id")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tool Resources和初始消息同时处理 - 验证复杂创建场景")
    void shouldHandleBothToolResourcesAndMessages() throws Exception {
        /*
         * 测试重点：
         * 1. Thread创建时同时处理tool_resources和messages
         * 2. 验证ThreadService.createThread的完整流程
         * 3. 确保两个复杂功能不会相互干扰
         * 
         * 验证场景：
         * - tool_resources -> ThreadFileRelation表
         * - messages -> Message表  
         * - 两者都正确关联到新创建的Thread
         */
        String requestBody = loadTestData("thread-create-full.json");

        MvcResult result = mockMvc.perform(addAuthHeader(post("/v1/threads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String threadId = json.get("id").asText();

        // 验证tool_resources正确处理
        mockMvc.perform(addAuthHeader(get("/v1/threads/" + threadId)))
                .andExpect(status().isOk())
                .andExpect(result2 -> {
                    String getResponse = result2.getResponse().getContentAsString();
                    JsonNode getJson = objectMapper.readTree(getResponse);
                    
                    // 验证tool_resources存在
                    JsonNode toolResources = getJson.get("tool_resources");
                    assert toolResources.has("code_interpreter");
                    
                    // 验证文件ID正确
                    JsonNode codeFiles = toolResources.get("code_interpreter").get("file_ids");
                    assert codeFiles.isArray();
                    assert !codeFiles.isEmpty();
                });

        // 验证初始消息正确创建
        mockMvc.perform(addAuthHeader(get("/v1/threads/" + threadId + "/messages")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andExpect(jsonPath("$.data[0].role").value("user"))
                .andExpect(jsonPath("$.data[0].thread_id").value(threadId));
    }
}
