package com.ke.assistant.core.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolResult {
    private final ToolResultType type;
    private final Object message;
    private final String error;
    private final Map<String, String> meta;

    public boolean isNull() {
        return message == null && error == null;
    }


    @Getter
    @AllArgsConstructor
    public enum ToolResultType {
        text("text"),
        image_file("image/jpg"),
        link("text"),
        blob("text"),
        image_url("image/jpg")
        ;
        private final String defaultMineType;
    }

    public ToolResult(ToolResultType type, Object message) {
        this.type = type;
        this.message = message;
        this.error = null;
        this.meta = new HashMap<>();
        this.meta.put("mime_type", type.getDefaultMineType());
    }

    public ToolResult(ToolResultType type, Object message, String mineType) {
        this.type = type;
        this.message = message;
        this.error = null;
        this.meta = new HashMap<>();
        this.meta.put("mime_type", mineType);
    }

    public ToolResult(String error) {
        this.error = error;
        this.type = ToolResultType.text;
        this.message = error;
        this.meta = new HashMap<>();
    }

}
