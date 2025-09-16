package com.ke.assistant.dto.memory;

import com.ke.assistant.enums.MemoryType;
import lombok.Data;

import javax.validation.Valid;

@Data
public class MemoryRequest {

    private String user;

    private String threadId;
    
    private String query;
    
    private MemoryType type = MemoryType.MIX_MEMORY;
    
    @Valid
    private StrategyParam strategyParam = new StrategyParam();
    
    public boolean invalidQuery() {
        return type == MemoryType.LONG_MEMORY && (query == null || query.trim().isEmpty());
    }
}
