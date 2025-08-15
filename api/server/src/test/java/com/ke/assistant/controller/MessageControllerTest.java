package com.ke.assistant.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
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
 * MessageController 核心业务逻辑测试
 * 重点测试：线程归属验证、复杂内容格式处理、附件/元数据序列化
 */
class MessageControllerTest extends BaseControllerTest {

    // 统一的测试Thread ID
    private String testThreadId;

    @BeforeEach
    void setUp() throws Exception {
        // 在每个测试方法执行前创建一个Thread
        testThreadId = createThread();
    }

    private String createThread() throws Exception {
        String createThreadBody = loadTestData("thread-create-basic.json");
        
        MvcResult result = mockMvc.perform(addAuthHeader(post("/v1/threads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createThreadBody)))
                .andExpect(status().isOk())
                .andReturn();
                
        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("id").asText();
    }

    @Test
    @DisplayName("创建Message - Thread不存在应返回404")
    void shouldReturn404WhenCreatingMessageWithNonexistentThread() throws Exception {
        String nonexistentThreadId = "thread_nonexistent";
        String requestBody = loadTestData("message-create-basic.json");

        mockMvc.perform(addAuthHeader(post("/v1/threads/{thread_id}/messages", nonexistentThreadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("创建基础Message - 字符串内容")
    void shouldCreateBasicMessageWithStringContent() throws Exception {
        String requestBody = loadTestData("message-create-basic.json");

        mockMvc.perform(addAuthHeader(post("/v1/threads/{thread_id}/messages", testThreadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.object").value("thread.message"))
                .andExpect(jsonPath("$.thread_id").value(testThreadId))
                .andExpect(jsonPath("$.role").value("user"))
                .andExpect(jsonPath("$.content").exists())
                .andExpect(jsonPath("$.created_at").isNumber());
    }

    @Test
    @DisplayName("创建带多媒体内容的Message - 验证内容格式处理")
    void shouldCreateMessageWithMultiMediaContent() throws Exception {
        String requestBody = loadTestData("message-create-multimedia.json");

        MvcResult result = mockMvc.perform(addAuthHeader(post("/v1/threads/{thread_id}/messages", testThreadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String messageId = json.get("id").asText();

        // 验证内容格式正确转换
        mockMvc.perform(addAuthHeader(get("/v1/threads/{thread_id}/messages/{message_id}", testThreadId, messageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].type").value("text"))
                .andExpect(jsonPath("$.content[0].text.value").value("Please analyze this image:"));
    }

    @Test
    @DisplayName("创建带附件的Message - 验证附件序列化")
    void shouldCreateMessageWithAttachments() throws Exception {
        String requestBody = loadTestData("message-create-with-attachments.json");

        MvcResult result = mockMvc.perform(addAuthHeader(post("/v1/threads/{thread_id}/messages", testThreadId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments").isArray())
                .andExpect(jsonPath("$.attachments").isNotEmpty())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String messageId = json.get("id").asText();

        // 验证附件正确保存和反序列化
        mockMvc.perform(addAuthHeader(get("/v1/threads/{thread_id}/messages/{message_id}", testThreadId, messageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments[0].file_id").value("file_abc123"))
                .andExpect(jsonPath("$.attachments[0].tools[0].type").value("code_interpreter"));
    }

    @Test
    @DisplayName("创建带复杂元数据的Message - 验证元数据JSON序列化")
    void shouldCreateMessageWithComplexMetadata() throws Exception {
        String requestBody = loadTestData("message-create-complex-metadata.json");

        MvcResult result = mockMvc.perform(addAuthHeader(post("/v1/threads/{thread_id}/messages", testThreadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);

        // 验证复杂元数据正确序列化/反序列化
        assert json.get("metadata").get("priority").asText().equals("high");
        assert json.get("metadata").get("tags").isArray();
        assert json.get("metadata").get("config").get("temperature").asDouble() == 0.7;
        assert json.get("metadata").get("nested").get("level1").get("level2").asText().equals("deep_value");
    }

    @Test
    @DisplayName("获取Message - 成功获取")
    void shouldGetMessageSuccessfully() throws Exception {
        // 先创建一个Message
        String createBody = loadTestData("message-create-basic.json");
        MvcResult createResult = mockMvc.perform(addAuthHeader(post("/v1/threads/{thread_id}/messages", testThreadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)))
                .andReturn();

        String realMessageId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // 获取Message
        mockMvc.perform(addAuthHeader(get("/v1/threads/{thread_id}/messages/{message_id}", testThreadId, realMessageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(realMessageId))
                .andExpect(jsonPath("$.thread_id").value(testThreadId))
                .andExpect(jsonPath("$.role").value("user"));
    }

    @Test
    @DisplayName("获取Message - 消息不存在")
    void shouldReturn404WhenMessageNotFound() throws Exception {
        String nonexistentMessageId = "msg_nonexistent";

        mockMvc.perform(addAuthHeader(get("/v1/threads/{thread_id}/messages/{message_id}", testThreadId, nonexistentMessageId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("获取Message - 线程归属验证失败")
    void shouldReturn400WhenMessageDoesNotBelongToThread() throws Exception {
        // 创建第二个Thread
        String wrongThreadId = createThread();
        
        // 在第一个Thread下创建Message
        String createBody = loadTestData("message-create-basic.json");
        MvcResult createResult = mockMvc.perform(addAuthHeader(post("/v1/threads/{thread_id}/messages", testThreadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)))
                .andReturn();

        String messageId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // 尝试用第二个Thread的ID获取属于第一个Thread的Message
        mockMvc.perform(addAuthHeader(get("/v1/threads/{thread_id}/messages/{message_id}", wrongThreadId, messageId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("更新Message - 更新内容和元数据")
    void shouldUpdateMessageContentAndMetadata() throws Exception {
        // 1. 先创建一个Message
        String createBody = loadTestData("message-create-basic.json");
        MvcResult createResult = mockMvc.perform(addAuthHeader(post("/v1/threads/{thread_id}/messages", testThreadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)))
                .andReturn();

        String messageId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // 2. 更新Message
        String updateBody = loadTestData("message-update-content-metadata.json");
        mockMvc.perform(addAuthHeader(post("/v1/threads/{thread_id}/messages/{message_id}", testThreadId, messageId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(messageId))
                .andExpect(jsonPath("$.metadata.edited").value(true));

        // 3. 验证更新生效
        mockMvc.perform(addAuthHeader(get("/v1/threads/{thread_id}/messages/{message_id}", testThreadId, messageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.edited").value(true))
                .andExpect(jsonPath("$.metadata.edit_time").exists());
    }

    @Test
    @DisplayName("更新Message - 线程归属验证失败")
    void shouldReturn400WhenUpdatingMessageWithWrongThreadId() throws Exception {
        String wrongThreadId = "thread_update_wrong";

        // 1. 先在正确的线程下创建Message
        String createBody = loadTestData("message-create-basic.json");
        MvcResult createResult = mockMvc.perform(addAuthHeader(post("/v1/threads/{thread_id}/messages", testThreadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)))
                .andReturn();

        String messageId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // 2. 尝试用错误的thread_id更新Message
        String updateBody = loadTestData("message-update-content-metadata.json");
        mockMvc.perform(addAuthHeader(post("/v1/threads/{thread_id}/messages/{message_id}", wrongThreadId, messageId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("删除Message - 成功删除")
    void shouldDeleteMessageSuccessfully() throws Exception {
        // 1. 先创建一个Message
        String createBody = loadTestData("message-create-basic.json");
        MvcResult createResult = mockMvc.perform(addAuthHeader(post("/v1/threads/{thread_id}/messages", testThreadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)))
                .andReturn();

        String messageId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // 2. 删除Message
        mockMvc.perform(addAuthHeader(delete("/v1/threads/{thread_id}/messages/{message_id}", testThreadId, messageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(messageId))
                .andExpect(jsonPath("$.object").value("thread.message"))
                .andExpect(jsonPath("$.deleted").value(true));

        // 3. 验证Message已被删除
        mockMvc.perform(addAuthHeader(get("/v1/threads/{thread_id}/messages/{message_id}", testThreadId, messageId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("删除Message - 线程归属验证失败")
    void shouldReturn400WhenDeletingMessageWithWrongThreadId() throws Exception {
        String wrongThreadId = "thread_delete_wrong";

        // 1. 先在正确的线程下创建Message
        String createBody = loadTestData("message-create-basic.json");
        MvcResult createResult = mockMvc.perform(addAuthHeader(post("/v1/threads/{thread_id}/messages", testThreadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)))
                .andReturn();

        String messageId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // 2. 尝试用错误的thread_id删除Message
        mockMvc.perform(addAuthHeader(delete("/v1/threads/{thread_id}/messages/{message_id}", wrongThreadId, messageId)))
                .andExpect(status().isBadRequest());

        // 3. 验证Message仍然存在
        mockMvc.perform(addAuthHeader(get("/v1/threads/{thread_id}/messages/{message_id}", testThreadId, messageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(messageId));
    }

    @Test
    @DisplayName("删除Message - 消息不存在")
    void shouldReturn404WhenDeletingNonexistentMessage() throws Exception {
        // 使用统一的testThreadId
        String nonexistentMessageId = "msg_nonexistent_delete";

        mockMvc.perform(addAuthHeader(delete("/v1/threads/{thread_id}/messages/{message_id}", testThreadId, nonexistentMessageId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("内容格式复杂场景 - 字符串转多媒体内容验证")
    void shouldHandleComplexContentFormatTransformation() throws Exception {
        /*
         * 测试重点：
         * 1. 字符串内容 -> MessageContentText格式转换
         * 2. MultiMediaContent列表 -> 正确的嵌套结构
         * 3. formatMessageContent方法的正确性
         * 4. 存储和检索时的序列化/反序列化
         * 
         * 验证场景：
         * - 输入字符串"Hello world" 
         * - 转换为：[{"type": "text", "text": {"value": "Hello world", "annotations": []}}]
         * - 存储到数据库后能正确反序列化
         */
        String requestBody = loadTestData("message-create-string-to-format.json");

        MvcResult result = mockMvc.perform(addAuthHeader(post("/v1/threads/{thread_id}/messages", testThreadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String messageId = json.get("id").asText();

        // 验证内容格式转换正确
        mockMvc.perform(addAuthHeader(get("/v1/threads/{thread_id}/messages/{message_id}", testThreadId, messageId)))
                .andExpect(status().isOk())
                .andExpect(result2 -> {
                    String getResponse = result2.getResponse().getContentAsString();
                    JsonNode getJson = objectMapper.readTree(getResponse);
                    
                    // 验证内容被正确转换为数组格式
                    JsonNode content = getJson.get("content");
                    assert content.isArray();
                    assert content.size() == 1;
                    
                    // 验证转换后的结构
                    JsonNode firstItem = content.get(0);
                    assert firstItem.get("type").asText().equals("text");
                    assert firstItem.has("text");
                    assert firstItem.get("text").get("value").asText().equals("Hello world for format test");
                    assert firstItem.get("text").get("annotations").isArray();
                });
    }

    @Test
    @DisplayName("推理内容处理 - reasoning_content字段验证")
    void shouldHandleReasoningContentCorrectly() throws Exception {
        // 使用统一的testThreadId
        String requestBody = loadTestData("message-create-with-reasoning.json");

        MvcResult result = mockMvc.perform(addAuthHeader(post("/v1/threads/{thread_id}/messages", testThreadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String messageId = json.get("id").asText();

        // 验证推理内容正确保存
        mockMvc.perform(addAuthHeader(get("/v1/threads/{thread_id}/messages/{message_id}", testThreadId, messageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasoning_content").value("This is my reasoning process for the response..."));
    }
}
