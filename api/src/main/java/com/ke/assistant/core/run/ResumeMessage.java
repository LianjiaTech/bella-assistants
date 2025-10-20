package com.ke.assistant.core.run;

import com.theokanning.openai.assistants.run.Run;
import com.theokanning.openai.assistants.run_step.RunStep;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResumeMessage {
    private Run run;
    private RunStep runStep;
}
