package com.ke.assistant.model;

import com.theokanning.openai.assistants.message.Message;
import com.theokanning.openai.response.Response;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result of creating a response
 */
@Data
@Builder
public class ResponseCreateResult {
    
    /**
     * The created response object
     */
    private Response response;
    
    /**
     * Thread ID used for execution
     */
    private String threadId;
    
    /**
     * Run ID created for execution
     */
    private String runId;
    
    /**
     * Response ID
     */
    private String responseId;
    
    /**
     * Assistant message ID for streaming
     */
    private String assistantMessageId;
    
    /**
     * Additional messages to process
     */
    private List<Message> additionalMessages;
    
    /**
     * Whether a new thread was created
     */
    private boolean isNewThread;
}