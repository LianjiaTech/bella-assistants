package com.ke.assistant.model;


import com.theokanning.openai.assistants.message.Message;
import com.theokanning.openai.assistants.run.Run;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class RunCreateResult {
    private Run run;
    private String assistantMessageId;
    private List<Message> additionalMessages;
}
