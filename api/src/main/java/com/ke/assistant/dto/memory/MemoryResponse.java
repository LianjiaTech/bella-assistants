package com.ke.assistant.dto.memory;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemoryResponse {
    private List<Map<String, Object>> longMemory;
    private List<Map<String, Object>> shortMemory;
}
