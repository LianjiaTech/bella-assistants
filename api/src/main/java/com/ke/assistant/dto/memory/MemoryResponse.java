package com.ke.assistant.dto.memory;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemoryResponse {
    @JsonProperty("long_memory")
    private List<Map<String, Object>> longMemory;
    @JsonProperty("short_memory")
    private List<Map<String, Object>> shortMemory;
}
