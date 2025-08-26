package com.ke.assistant.core.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ToolResult {
    private Object output;
    private String error;

    public boolean isNull() {
        return output == null && error == null;
    }
}
