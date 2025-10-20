package com.ke.assistant.model;

import java.util.List;

import com.theokanning.openai.assistants.message.Message;
import com.theokanning.openai.assistants.run.Run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class RunCreateResult {
    private Run run;
    private String assistantMessageId;
    private List<Message> additionalMessages;
}
