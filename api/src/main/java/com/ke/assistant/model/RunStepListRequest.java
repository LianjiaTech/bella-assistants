package com.ke.assistant.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Request model for listing run steps by multiple run IDs
 */
@Data
public class RunStepListRequest {
    
    /**
     * List of run IDs to query run steps for
     */
    @JsonProperty("run_ids")
    private List<String> runIds;
}
